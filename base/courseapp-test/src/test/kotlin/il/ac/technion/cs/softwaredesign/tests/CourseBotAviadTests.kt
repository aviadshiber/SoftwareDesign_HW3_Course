package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.lessThan
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class CourseBotAviadTests {
    private val injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
    }

    private val courseApp = injector.getInstance<CourseApp>()
    private val bots = injector.getInstance<CourseBots>()
    private val messageFactory = injector.getInstance<MessageFactory>()

    init {
        bots.prepare().join()
        bots.start().join()
    }
    @Test
    fun `calc trigger return previous phase`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("aviad", "shiber").thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel") }.join()
        var lastTrigger = bots.bot().thenCompose { bot -> bot.join("#channel").thenCompose { bot.setCalculationTrigger("calculate") } }.join()
        assertThat(lastTrigger, absent())
        courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 2+ 20*3".toByteArray()).join()) }
                .join()
        lastTrigger = bots.bot("Anna0").thenCompose { bot -> bot.setCalculationTrigger("otherCalc") }.join()
        assertThat(lastTrigger, present(equalTo("calculate")))
        lastTrigger = bots.bot("Anna0").thenCompose { bot -> bot.setCalculationTrigger("otherCalc") }.join()
        assertThat(lastTrigger, present(equalTo("otherCalc")))
        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == 2 + 20 * 3 })
        }
    }

    @Test
    fun `bot cannot join to a channel that does not exist`() {
        courseApp.login("aviad", "hunter2").thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }.join()
        val bot = bots.bot().thenCompose { bot -> bot.join("#channel").thenApply { bot } }.join()

        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(ofSeconds(10)) {
                bot.join("#notExistingChannel").joinException()
                bot.join("notExistingChannel").joinException()
            }
        }
    }

    @Test
    fun `bot cannot do action on a a channel after he was kicked`() {
        val channelName = "#channel"
        val adminToken = courseApp.login("aviad", "hunter2").thenCompose { adminToken -> courseApp.channelJoin(adminToken, channelName).thenApply { adminToken } }.join()
        val bot = bots.bot("mybot").thenCompose { bot -> bot.join(channelName).thenApply { bot } }.join()
        courseApp.channelKick(adminToken, channelName, "mybot")
        assertThrows<NoSuchEntityException> { runWithTimeout(ofSeconds(10)) { bot.mostActiveUser(channelName).joinException() } }
        assertThrows<NoSuchEntityException> { runWithTimeout(ofSeconds(10)) { bot.richestUser(channelName).joinException() } }
        assertThrows<NoSuchEntityException> { runWithTimeout(ofSeconds(10)) { bot.runSurvey(channelName, "why they torches us?!", listOf("because they can")).joinException() } }
    }

    @Test
    fun `user can not tip more than he have`() {
        val aviad = courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .thenCompose { bot -> bot.setTipTrigger("tip").thenApply { bot } }.join()
        val shahar = courseApp.login("shahar", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 800 aviad").thenApply { token } }
                .join()
        sendToChannel(shahar, "#channel", "tip 400 aviad").join()
        //at this point aviad should have 1800$ because shahar dont have 400$ to give


        courseApp.login("ron", "pass").thenCompose { token -> courseApp.channelJoin(token, "#channel") }.join()
        courseApp.login("matan", "pass").thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 1000 ron") }.join()

        //at this point ron have 2000$ and aviad have 1800$
        val richest: String? = bot.richestUser("#channel").get()
        assertThat(richest, present(equalTo("ron")))
    }

    @Test
    fun `if no tip was triggered then richestUser returns null`() {
        courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .thenCompose { bot -> bot.setTipTrigger("tip").thenApply { bot } }.join()
        courseApp.login("shahar", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 1000 notExistingUser") }
                .join()
        val richest: String? = bot.richestUser("#channel").get()
        assertThat(richest, absent())
    }

    @Test
    fun `bot cash tracks get reset after bot leave channel`() {
        courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .thenCompose { bot -> bot.setTipTrigger("tip").thenApply { bot } }.join()
        courseApp.login("shahar", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 1000 aviad") }
                .join()
        courseApp.login("ron", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 500 shahar") }
                .join()
        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.richestUser("#channel").get()
        }, equalTo("aviad"))

        reconnectBotInChannel(bot, "#channel").join()
        val newRichest: String? = bot.richestUser("#channel").get()
        assertThat(newRichest, absent())
    }

    @Test
    fun `bot tracks for richest user but both users have same amount of cash so no such user exist`() {
        val adminToken = courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .thenCompose { bot -> bot.setTipTrigger("tip").thenApply { bot } }.join()
        courseApp.login("shahar", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 1000 aviad") }.join()
        courseApp.login("ron", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 1000 shahar") }.join()
        courseApp.login("gal", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "tip 1000 shahar") }.join()
        val richest: String? = bot.richestUser("#channel").get()

        assertThat(richest, absent())
    }

    private fun reconnectBotInChannel(bot: CourseBot, channel: String): CompletableFuture<Unit> {
        return bot.part(channel).thenCompose { bot.join(channel) }
    }

    @Test
    fun `bot start counting on channel, then we restore the bot and count the messages`() {
        courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }.join()

        bots.bot()
                .thenCompose { bot ->
                    bot.join("#channel").thenApply { bot }.thenCompose { bot.beginCount("#channel", "hello", MediaType.TEXT) }
                }
                .thenCompose { courseApp.login("shahar", "pass").thenCompose { token -> joinChannelAndSendAsUser(token, "#channel", "hello") } }.join()



        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count("#channel", "hello", MediaType.TEXT) }.get()
        }, equalTo(1L))


    }

    @Test
    fun `A user in the channel can ask the bot to do calculation- checking parentheses precedence`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate (20 * (2 + 2)/2)+1".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == (20 * (2 + 2) / 2) + 1 })
        }
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation- checking arithmetic precedence`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 1+2 * 2/2+1".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == ((20 * 1) + ((2 * 2) / 2) + 1) })
        }
    }


    @Test
    fun `A user in the channel can ask the bot to do calculation with bad regex pattern`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate [20 * 1+2 * 2/2+1]".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())

        }
        verify(exactly = 0) { listener.invoke("#channel@Anna0", any()) }
    }

    @Test
    fun `Can create a bot and add make it join channels`() {
        val token = courseApp.login("gal", "hunter2").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            val bot = courseApp.channelJoin(token, "#channel")
                    .thenCompose { bots.bot() }
                    .join()
            bot.join("#channel").join()
            bot.channels().join()
        }, equalTo(listOf("#channel")))
    }

    @Test
    fun `Can list bots in a channel in creation order`() {
        courseApp.login("aviad", "shiber")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
                            .thenCompose { bots.bot("ronBot").thenCompose { it.join("#channel") } }
                            .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
                            .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bots("#channel").join()
        }, equalTo(listOf("Anna0", "ronBot", "Anna2", "Anna3")))
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 2 + 2".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == 42 })
        }
    }

    @Test
    fun `A user in the channel can tip another user`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot ->
                                            bot.join("#channel").thenApply { bot }
                                        }
                                        .thenCompose { bot -> bot.setTipTrigger("tip") }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token ->
                                courseApp.channelJoin(token, "#channel").thenApply { token }
                            }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0")
                    .thenCompose { it.richestUser("#channel") }
                    .join()
        }, present(equalTo("gal")))
    }

    private fun joinChannelAndSendAsUser(userToken: String, channel: String, content: String): CompletableFuture<Message> {
        return courseApp.channelJoin(userToken, channel).thenCompose { sendToChannel(userToken, channel, content) }
    }

    private fun sendToChannel(userToken: String, channel: String, content: String) =
            messageFactory.create(MediaType.TEXT, content.toByteArray()).thenCompose { m -> courseApp.channelSend(userToken, channel, m).thenApply { m } }


    @Test
    fun `The bot accurately tracks keywords`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, channel)
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot -> bot.join(channel).thenApply { bot } }
                                        .thenCompose { bot -> bot.beginCount(regex = regex) }
                            }
                            .thenCompose { courseApp.login("matan", "pass").thenCompose { token -> joinChannelAndSendAsUser(token, channel, "hello, world!") } }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count(regex = regex) }.join()
        }, equalTo(1L))
    }

    @Test
    fun `A user in the channel can ask the bot to do a survey`() {
        val adminToken = courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(0L, 0L, 2L))
    }

    @Test
    fun `A user in the channel can override his answer, and cannot vote more than once`() {
        val adminToken = courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()
        val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                listOf("Cranberry",
                        "Charcoal",
                        "Chocolate-chip Mint")).join()
        assertThat(runWithTimeout(ofSeconds(10)) {
            sendToChannel(adminToken, "#channel", "Chocolate-chip Mint").join()
            sendToChannel(regularUserToken, "#channel", "Chocolate-chip Mint").join()
            sendToChannel(adminToken, "#channel", "Chocolate-chip Mint").join()
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(0L, 0L, 2L))

        assertThat(runWithTimeout(ofSeconds(10)) {
            sendToChannel(adminToken, "#channel", "Cranberry").join()
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(1L, 0L, 1L))
    }

    @Test
    fun `requesting results from unExisting survey result in NoSuchEntityException`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()
        bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                listOf("Cranberry",
                        "Charcoal",
                        "Chocolate-chip Mint")).join()

        assertThrows<NoSuchEntityException> { runWithTimeout(ofSeconds(10)) { bot.surveyResults("notExisting").joinException() } }
    }

    @Test
    fun `throws NoSuchEntityException if invalid token accepted`() {
        val bot = bots.bot("Cashier").join()
        val channel = "#shop"

        val user = courseApp.login("user", "1234").join()
        val otherUser = courseApp.login("otherUser", "1234").join()

        bot.setTipTrigger("tip")
                .thenCompose { bot.join(channel) }
                .thenCompose { courseApp.channelJoin(user, channel) }
                .thenCompose { courseApp.channelJoin(otherUser, channel) }
                .thenCompose { messageFactory.create(MediaType.TEXT, "tip 1 user".toByteArray()) }
                // user = 1001
                // otherUser = 999
                .thenCompose { m -> courseApp.channelSend(otherUser, channel, m) }
                .thenCompose { messageFactory.create(MediaType.TEXT, "tip 500 otherUser".toByteArray()) }
                // user = 501
                // otherUser = 1499
                .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                .thenCompose { bot.part(channel) }
                .join()

        assertThrows<NoSuchEntityException> {
            bot.richestUser(channel+channel).joinException()
        }
    }

    @Test
    fun `requesting results of survey from the wrong bot results end in NoSuchEntityException`() {
        val adminToken = courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot1 = bots.bot("bot1")
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()
        val bot2 = bots.bot("bot2")
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()
        val survey = bot1.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                listOf("Cranberry",
                        "Charcoal",
                        "Chocolate-chip Mint")).join()

        assertThrows<NoSuchEntityException> {
            runWithTimeout(ofSeconds(10)) {
                sendToChannel(adminToken, "#channel", "Chocolate-chip Mint").join()
                sendToChannel(regularUserToken, "#channel", "Chocolate-chip Mint").join()
                sendToChannel(adminToken, "#channel", "Chocolate-chip Mint").join()
                bot2.surveyResults(survey).joinException()
            }
        }

    }

    @Test
    fun `bot can report on last message created that he saw, even after he leave the channel`() {
        val channelName = "#channel"
        val adminToken = courseApp.login("admin", "pass")
                .thenCompose { token -> courseApp.channelJoin(token, channelName).thenApply { token } }
                .join()
        courseApp.login("ron", "123")
                .thenCompose { token -> courseApp.channelJoin(token, channelName).thenApply { token } }
                .join()
        val bot1 = bots.bot("bot1")
                .thenCompose { bot -> bot.join(channelName).thenApply { bot } }
                .join()

        val m1 = sendToChannel(adminToken, channelName, "message1").join()
        val seenTime1 = bot1.seenTime("admin").join()
        assertThat(m1.created, present(equalTo(seenTime1)))
        Thread.sleep(TimeUnit.SECONDS.toMillis(2))
        val m2 = sendToChannel(adminToken, channelName, "message2").join()
        val seenTime2 = bot1.seenTime("admin").join()
        assertThat(m2.created, present(equalTo(seenTime2)))

        assertThat(seenTime1!!, present(lessThan(seenTime2!!)))
        bot1.part(channelName).join()
        val seenTime3 = bot1.seenTime("admin").join()
        assertThat(m2.created, present(equalTo(seenTime3)))
    }

    @Test
    fun `bot can track MostActiveUser`() {
        val channelName = "#channel"
        val adminToken = courseApp.login("admin", "pass")
                .thenCompose { token -> courseApp.channelJoin(token, channelName).thenApply { token } }
                .join()
        val aviad = courseApp.login("aviad", "123")
                .thenCompose { token -> courseApp.channelJoin(token, channelName).thenApply { token } }
                .join()
        val bot = bots.bot("bot1")
                .thenCompose { bot -> bot.join(channelName).thenApply { bot } }
                .join()
        sendToChannel(adminToken, channelName, "message#1 from admin").join()
        sendToChannel(adminToken, channelName, "message#2 from admin").join()
        sendToChannel(aviad, channelName, "message#1 from aviad").join()
        sendToChannel(adminToken, channelName, "message#3 from admin").join()
        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.mostActiveUser(channelName).join()
        }, present(equalTo("admin")))
        sendToChannel(aviad, channelName, "message#2 from aviad").join()
        sendToChannel(aviad, channelName, "message#3 from aviad").join()
        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.mostActiveUser(channelName).join()
        }, absent())
        sendToChannel(aviad, channelName, "message#4 from aviad").join()
        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.mostActiveUser(channelName).join()
        }, present(equalTo("aviad")))
    }

@Test
    fun `bot join part and join`() {
        val channelName = "#channel"
        val bot = bots.bot("bot1")
                .thenCompose { bot -> bot.join(channelName).thenApply { bot } }
                .thenCompose { bot -> bot.part(channelName).thenApply { bot } }
                .thenCompose { bot -> bot.join(channelName).thenApply { bot } }
                .thenCompose { bot -> bot.join(channelName).thenApply { bot } }
                .thenCompose { bot -> bot.join(channelName).thenApply { bot } }
                .join()
        bots.bot("bot22")
            .thenCompose { b -> b.join(channelName + channelName)}
        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bots(channelName).join()
        }, equalTo(listOf("bot1")))
        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bots(null).join()
        }, equalTo(listOf("bot1", "bot22")))
    }

    @Test
    fun `after part, global counter didbt change`() {
        val regex = ".*ello.*[wW]orl.*"
        val channelName = "#channel"
        val adminToken = courseApp.login("admin", "pass")
                .thenCompose { token -> courseApp.channelJoin(token, channelName).thenApply { token } }
                .join()
        val aviad = courseApp.login("aviad", "123")
                .thenCompose { token -> courseApp.channelJoin(token, channelName).thenApply { token } }
                .join()
        val ron = courseApp.login("ron", "ron123")
                .thenCompose { token -> courseApp.channelJoin(token, channelName).thenApply { token } }
                .join()


        val bot = bots.bot("bot1")
                .thenCompose { bot -> bot.join(channelName).thenApply { bot } }
                .thenCompose { bot -> bot.beginCount(null, regex, mediaType = MediaType.TEXT).thenApply { bot } }
                .thenCompose { bot -> bot.beginCount(channelName, regex, mediaType = MediaType.TEXT).thenApply { bot } }
                .thenCompose { bot -> sendToChannel(adminToken, channelName, "hello, World").thenApply { bot } }
                .thenCompose { bot -> sendToChannel(aviad, channelName, "hello, World").thenApply { bot } }
                .thenCompose { bot -> sendToChannel(ron, channelName, "hello, World").thenApply { bot } }
                .thenCompose { bot -> sendToChannel(ron, channelName, "hello, World").thenApply { bot } }
                .thenCompose { bot -> sendToChannel(ron, channelName, "hello, World").thenApply { bot } }
                //.thenCompose { bot -> bot.part(channelName).thenApply { bot } }
                .join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.count(channelName, regex, MediaType.TEXT).join()
        }, equalTo(5L))

        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.count(null, regex, MediaType.TEXT).join()
        }, equalTo(5L))

        bot.part(channelName).join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.count(channelName, regex, MediaType.TEXT).join()
        }, equalTo(0L))

        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.count(null, regex, MediaType.TEXT).join()
        }, equalTo(5L))
    }
}
