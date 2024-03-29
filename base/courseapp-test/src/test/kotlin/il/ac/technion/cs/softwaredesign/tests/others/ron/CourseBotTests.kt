package il.ac.technion.cs.softwaredesign.tests.others.ron


import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.tests.TestModule
import il.ac.technion.cs.softwaredesign.tests.joinException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseBotTests {
    private val injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())
    private var courseApp: CourseApp
    private var bots: CourseBots
    private var messageFactory: MessageFactory

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
        courseApp = injector.getInstance()
        bots = injector.getInstance()
        messageFactory = injector.getInstance()
    }


    @Test
    @Order(1)
    fun `doing nothing here`() {

    }

    @Test
    @Order(2)
    fun `Can create a bot and add make it join channels`() {
        val token = courseApp.login("gal", "hunter2").join()
        val bot = courseApp.channelJoin(token, "#channel")
                .thenCompose { bots.bot() }
                .join()
        bot.join("#channel").join()
        assertEquals(listOf("#channel"), bot.channels().join())
    }

    @Test
    @Order(3)
    fun `Can list bot in a channel, default name checked`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel").join()
        val bot = bots.bot().join()
        bot.join("#channel").join()

        assertEquals(listOf("Anna0"), bots.bots("#channel").join())
    }

    @Test
    @Order(4)
    fun `bot joins a channel twice, affects as joining once`() {
        val token = courseApp.login("gal", "hunter2").join()
        val bot = courseApp.channelJoin(token, "#channel")
                .thenCompose { bots.bot() }
                .join()

        bot.join("#channel").join()
        bot.join("#channel").join()
        assertEquals(listOf("#channel"), bot.channels().join())
    }

    @Test
    @Order(5)
    fun `bot joins a channel once, then leave it have empty list of channels`() {
        val token = courseApp.login("gal", "hunter2").join()
        val bot = courseApp.channelJoin(token, "#channel")
                .thenCompose { bots.bot() }
                .join()

        bot.join("#channel").join()
        bot.part("#channel").join()
        assertEquals(listOf<String>(), bot.channels().join())
    }

    @Test
    @Order(6)
    fun `bot joins a channel once, then leave it twice throws exception`() {
        val token = courseApp.login("gal", "hunter2").join()
        val bot = courseApp.channelJoin(token, "#channel")
                .thenCompose { bots.bot() }
                .join()

        bot.join("#channel").join()
        bot.part("#channel").join()

        assertThrows<NoSuchEntityException> { bot.part("#channel").joinException() }
    }

    @Test
    @Order(6)
    fun `bot joins few channels and returns all channels in order of joining`() {
        val token = courseApp.login("gal", "hunter2").join()
        val bot = courseApp.channelJoin(token, "#channel1")
                .thenCompose { bots.bot() }
                .join()
        courseApp.channelJoin(token, "#channel2").join()


        bot.join("#channel2").join()
        bot.join("#channel1").join()

        assertEquals(listOf("#channel2", "#channel1"), bot.channels().join())
    }

    @Test
    @Order(7)
    fun `bot joins few channels and returns all channels after creating another bot with the same name`() {
        val token = courseApp.login("gal", "hunter2").join()
        val bot = courseApp.channelJoin(token, "#channel1")
                .thenCompose { bots.bot() }
                .join()
        courseApp.channelJoin(token, "#channel2").join()


        bot.join("#channel2").join()
        bot.join("#channel1").join()
        val otherBotDifName = bots.bot("Anna0").join()

        assertEquals(listOf("#channel2", "#channel1"), otherBotDifName.channels().join())
    }

    @Test
    @Order(8)
    fun `Can list bots in a channel, default name checked`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel").join()
        val bot1 = bots.bot().join()
        bot1.join("#channel").join()
        val bot2 = bots.bot().join()
        bot2.join("#channel").join()
        val bot3 = bots.bot().join()
        bot3.join("#channel").join()

        assertEquals(listOf("Anna0", "Anna1", "Anna2"), bots.bots("#channel").join())
    }

    @Test
    @Order(9)
    fun `Can list bots in a channel, bot names are sorted by creation time`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel").join()
        val bot1 = bots.bot("NotAnna").join()
        bot1.join("#channel").join()
        val bot2 = bots.bot().join()
        bot2.join("#channel").join()
        val bot3 = bots.bot().join()
        bot3.join("#channel").join()

        assertEquals(listOf("NotAnna", "Anna1", "Anna2"), bots.bots("#channel").join())
    }

    @Test
    @Order(10)
    fun `Can list bots in all system, bot names are sorted by creation time`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel").join()
        val bot1 = bots.bot("NotAnna").join()
        bot1.join("#channel").join()
        val bot2 = bots.bot().join()
        bot2.join("#channel").join()
        val bot3 = bots.bot().join()
        bot3.join("#channel").join()

        assertEquals(listOf("NotAnna", "Anna1", "Anna2"), bots.bots().join())
    }

    @Test
    @Order(11)
    fun `Can list bots in all system, bots in different channels maintain correct order`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        val bot1 = bots.bot("NotAnna").join()
        bot1.join("#channel1").join()
        val bot2 = bots.bot().join()
        bot2.join("#channel2").join()
        val bot3 = bots.bot().join()
        bot3.join("#channel1").join()

        assertEquals(listOf("NotAnna", "Anna1", "Anna2"), bots.bots().join())
    }

    @Test
    @Order(12)
    fun `Can list bots in all system, bots have not default names, bots in different channels maintain correct order`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        val bot1 = bots.bot("NotAnna").join()
        bot1.join("#channel1").join()
        val bot2 = bots.bot("random00").join()
        bot2.join("#channel2").join()
        val bot3 = bots.bot("whatwhat").join()
        bot3.join("#channel1").join()

        assertEquals(listOf("NotAnna", "random00", "whatwhat"), bots.bots().join())
    }

    @Test
    @Order(13)
    fun `The bot accurately tracks keywords - activate count with channel equals null`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel).join()
        val bot = bots.bot().join()
        bot.join(channel).join()
        bot.beginCount(channel, regex).join()
        val token = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(token, channel).join()
        courseApp.channelSend(token, channel, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()

        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel, regex = regex) }.join())
    }

    @Test
    @Order(14)
    fun `The bot accurately tracks keywords - activate count with channel `() {
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel).join()
        val bot = bots.bot().join()
        bot.join(channel).join()
        bot.beginCount(channel, regex).join()
        val token = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(token, channel).join()
        courseApp.channelSend(token, channel, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()

        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel, regex = regex) }.join())
    }

    @Test
    @Order(15)
    fun `beginCount gets null regex and null mediaType`() {
        val channel = "#channel"
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel).join()
        val bot = bots.bot().join()
        bot.join(channel).join()

        assertThrows<IllegalArgumentException> { bot.beginCount(channel).joinException() }
    }

    @Test
    @Order(16)
    fun `bot tracks messages in 2 channels, each channel got a matching message`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel1 = "#channel1"
        val channel2 = "#channel2"

        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel1).join()
        courseApp.channelJoin(adminToken, channel2).join()

        val bot = bots.bot().join()
        bot.join(channel1).join()
        bot.beginCount(channel1, regex).join()
        bot.join(channel2).join()
        bot.beginCount(channel2, regex).join()

        val token = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(token, channel1).join()
        courseApp.channelSend(token, channel1, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()
        courseApp.channelJoin(token, channel2).join()
        courseApp.channelSend(token, channel2, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()

        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel1, regex = regex) }.join())
        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel2, regex = regex) }.join())
//        assertEquals(2L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(regex = regex) }.join())
    }


    @Test
    @Order(17)
    fun `bot tracks messages in 2 channels, 1 message in first channel`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel1 = "#channel1"
        val channel2 = "#channel2"

        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel1).join()
        courseApp.channelJoin(adminToken, channel2).join()

        val bot = bots.bot().join()
        bot.join(channel1).join()
        bot.beginCount(channel1, regex).join()
        bot.join(channel2).join()
        bot.beginCount(channel2, regex).join()

        val token = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(token, channel1).join()
        courseApp.channelSend(token, channel1, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()
        //courseApp.channelJoin(token, channel2).join()
        //courseApp.channelSend(token, channel2, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()

        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel1, regex = regex) }.join())
        assertEquals(0L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel2, regex = regex) }.join())
//        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(regex = regex) }.join())
    }

    @Test
    @Order(18)
        fun `bot tracks messages in 2 channels, 1 message in second channel`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel1 = "#channel1"
        val channel2 = "#channel2"

        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel1).join()
        courseApp.channelJoin(adminToken, channel2).join()

        val bot = bots.bot().join()
        bot.join(channel1).join()
        bot.beginCount(channel1, regex).join()
        bot.join(channel2).join()
        bot.beginCount(channel2, regex).join()

        val token = courseApp.login("matan", "s3kr3t").join()
        //courseApp.channelJoin(token, channel1).join()
        //courseApp.channelSend(token, channel1, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()
        courseApp.channelJoin(token, channel2).join()
        courseApp.channelSend(token, channel2, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()

        assertEquals(0L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel1, regex = regex) }.join())
        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel2, regex = regex) }.join())
//        assertEquals(1L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(regex = regex) }.join())
    }

    @Test
    @Order(19)
    fun `bot ignores messages that are not matching by regular expression`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"


        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel).join()

        val bot = bots.bot().join()
        bot.join(channel).join()
        bot.beginCount(channel, regex).join()

        val token = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(token, channel).join()
        courseApp.channelSend(token, channel, messageFactory.create(MediaType.TEXT, "notHELLOWORLD!".toByteArray()).join()).join()

        assertEquals(0L, bots.bot("Anna0").thenCompose { bot2 -> bot2.count(channel = channel, regex = regex) }.join())
    }

    @Test
    @Order(20)
    fun `2 bots track a message from the same channel`() {  //TODO
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"


        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, channel).join()

        val bot1 = bots.bot().join()
        bot1.join(channel).join()
        bot1.beginCount(channel, regex).join()
        val bot2 = bots.bot().join()
        bot2.join(channel).join()
        bot2.beginCount(channel, regex).join()

        val token = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(token, channel).join()
        courseApp.channelSend(token, channel, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()).join()

        //assertEquals(1L, bots.bot("Anna0").thenCompose { bot -> bot.count(channel = channel,regex =  regex) }.join())
        assertEquals(1L, bots.bot("Anna1").thenCompose { bot -> bot.count(channel = channel, regex = regex) }.join())
    }

    @Test
    @Order(21)
    fun `A user in the channel can ask the bot to do calculation`() {//TODO
        val messages = mutableListOf<String>()
        val listener: ListenerCallback = { _, message ->
            messages.add(message.contents.toString(Charsets.UTF_8))
            CompletableFuture.completedFuture(Unit)
        }


        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenApply { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 2 + 1".toByteArray(Charsets.UTF_8)).join()) }
                }.join()
        assertEquals(2, messages.size)
        assertEquals(41.0, messages[0].toDouble())
    }

    @Test
    @Order(22)
    fun `A user in the channel can tip another user`() {
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        bot.setTipTrigger("tip").join()
        val otherToken = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(otherToken, "#channel").join()
        courseApp.channelSend(otherToken, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())

        val res = bot.richestUser("#channel").join()

        assertEquals("gal", res)
    }

    @Test
    @Order(23)
    fun `A user in the channel set trigger and gets old trigger null when first activated`() {
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        val resString = bot.setTipTrigger("tip").join()

        assertEquals(null, resString)
    }

    @Test
    @Order(24)
    fun `A user in the channel set trigger and gets old trigger after previously set`() {
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        bot.setTipTrigger("tip").join()
        val oldTrigger = bot.setTipTrigger("tippy").join()
        assertEquals("tip", oldTrigger)
    }

    @Test
    @Order(25)
    fun `richestUser activated on a bad channel - bot is not in channel should throw exception`() {
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        courseApp.channelJoin(adminToken, "#channel2").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        bot.setTipTrigger("tip").join()
        val otherToken = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(otherToken, "#channel").join()
        courseApp.channelSend(otherToken, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())

        assertThrows<NoSuchEntityException> { bot.richestUser("channel2").joinException() }
        assertThrows<NoSuchEntityException> { bot.richestUser("notExistChannel").joinException() }
    }


    @Test
    @Order(26)
    fun `seenTime returns null if a message by user was never seen and checks simple time ordering`() {
        val beforeTime = LocalDateTime.now()
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        courseApp.channelJoin(adminToken, "#channel2").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        bot.setTipTrigger("tip").join()
        val otherToken = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(otherToken, "#channel").join()
        courseApp.channelSend(otherToken, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())

        assertEquals(null, bot.seenTime("gal").join())
        assertNotNull(bot.seenTime("matan").join())
        val afterTime = LocalDateTime.now()
        assertTrue(bot.seenTime("matan").join()!! >= beforeTime)
        assertTrue(bot.seenTime("matan").join()!! <= afterTime)

    }

    @Test
    @Order(27)
    fun `mostActiveUser activated with a channel that bot is not a member of, throws exception`() {
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        courseApp.channelJoin(adminToken, "#channel2").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        bot.setTipTrigger("tip").join()
        val otherToken = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(otherToken, "#channel").join()
        // courseApp.channelSend(otherToken, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())

        assertThrows<NoSuchEntityException> { bot.mostActiveUser("#channel2").joinException() }
        assertThrows<NoSuchEntityException> { bot.mostActiveUser("#notExistChannel").joinException() }

    }

    @Test
    @Order(28)
    fun `mostActiveUser activated with a channel that was not used for messaging, bot is a member, returns null`() {
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        courseApp.channelJoin(adminToken, "#channel2").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        bot.setTipTrigger("tip").join()
        val otherToken = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(otherToken, "#channel").join()

        assertEquals(null, bot.mostActiveUser("#channel").join())
    }

    @Test
    @Order(29)
    fun `mostActiveUser returns most active user when user is in 2 channels`() {
        val adminToken = courseApp.login("gal", "hunter2").get()
        courseApp.channelJoin(adminToken, "#channel").get()
        courseApp.channelJoin(adminToken, "#channel2").get()
        val bot = bots.bot().get()
        bot.join("#channel").get()
        bot.setTipTrigger("tip").join()
        bot.join("#channel2").get()
        val otherToken = courseApp.login("matan", "s3kr3t").join()
        courseApp.channelJoin(otherToken, "#channel").join()
        courseApp.channelSend(otherToken, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())
        courseApp.channelSend(otherToken, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())

        val otherToken2 = courseApp.login("user2", "s3kr3t").join()
        courseApp.channelJoin(otherToken2, "#channel").join()
        courseApp.channelJoin(otherToken2, "#channel2").join()
        courseApp.channelSend(otherToken2, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())
        courseApp.channelSend(otherToken2, "#channel2", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())
        courseApp.channelSend(otherToken2, "#channel2", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join())
        //first user send 2 messages in "#channel", second user send 1 message in "#channel" and 2 messages in "#channel2"

        assertEquals("matan", bot.mostActiveUser("#channel").join())
        assertEquals("user2", bot.mostActiveUser("#channel2").join())
    }

    @Test
    @Order(30)
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

        assertDoesNotThrow {
            val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            assertEquals(mutableListOf(0L, 0L, 2L), bot.surveyResults(survey).join())
        }

    }

    @Test
    @Order(31)
    fun `A user in the channel can ask the bot to do a survey, change vote successfully`() {
        val adminToken = courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()

        assertDoesNotThrow {
            val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Cranberry".toByteArray()).join())
            assertEquals(mutableListOf(1L, 0L, 1L), bot.surveyResults(survey).join())
        }
    }

    @Test
    @Order(32)
    fun `A user in the channel can ask the bot to do a survey, change all votes successfully`() {
        val adminToken = courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()

        assertDoesNotThrow {
            val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Cranberry".toByteArray()).join())
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Charcoal".toByteArray()).join())
            assertEquals(mutableListOf(1L, 1L, 0L), bot.surveyResults(survey).join())
        }
    }

    @Test
    @Order(33)
    fun `runSurvey on a channel bot is not a member of`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()

        assertThrows<NoSuchEntityException> {
            bot.runSurvey("#channel2", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).joinException()

        }
        assertThrows<NoSuchEntityException> {
            bot.runSurvey("#notExist", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).joinException()

        }
    }

    @Test
    @Order(34)
    fun `surveyResults on a bad identifier throws exception`() {
        val adminToken = courseApp.login("gal", "hunter2").join()
        courseApp.channelJoin(adminToken, "#channel").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()


        val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                listOf("Cranberry",
                        "Charcoal",
                        "Chocolate-chip Mint")).join()

        assertThrows<NoSuchEntityException> {
            bot.surveyResults("wrong$survey").joinException()

        }
    }

}