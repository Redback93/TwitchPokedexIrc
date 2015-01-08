/**
 * Copyright (C) 2010-2013 Leon Blakey <lord.quackstar at gmail.com>
 *
 * This file is part of PircBotX.
 *
 * PircBotX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PircBotX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PircBotX. If not, see <http://www.gnu.org/licenses/>.
 */
package org.pircbotx.output;

import java.io.BufferedReader;
import java.io.IOException;
import org.pircbotx.hooks.managers.GenericListenerManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import javax.net.SocketFactory;
import org.apache.commons.lang3.StringUtils;
import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.mockito.Mockito.*;
import org.pircbotx.TestUtils;

/**
 * Test the output of PircBotX. Depend on ConnectTests to check mocked sockets
 * @author Leon Blakey <lord.quackstar at gmail.com>
 */
@Test(/*dependsOnGroups = "ConnectTests", */singleThreaded = true)
public class OutputTest {
	protected final String aString = "I'm some super long string that has multiple words";
	protected PircBotX bot;
	protected SocketFactory socketFactory;
	protected ByteArrayOutputStream botOut;
	protected User aUser;
	protected Channel aChannel;
	protected CountDownLatch inputLatch;
	protected InputStream in;

	@BeforeMethod
	public void botSetup() throws Exception {
		InetAddress localhost = InetAddress.getLocalHost();

		//Setup streams for bot
		inputLatch = new CountDownLatch(1);
		botOut = new ByteArrayOutputStream();
		in = new ByteArrayInputStream("".getBytes());
		Socket socket = mock(Socket.class);
		when(socket.isConnected()).thenReturn(true);
		when(socket.getInputStream()).thenReturn(in);
		when(socket.getOutputStream()).thenReturn(botOut);
		socketFactory = mock(SocketFactory.class);
		when(socketFactory.createSocket(localhost, 6667, null, 0)).thenReturn(socket);

		//Configure and connect bot
		bot = new PircBotX(TestUtils.generateConfigurationBuilder()
				.setCapEnabled(true)
				.setServer(localhost.getHostName(), 6667)
				.setServerPassword(null)
				.setSocketFactory(socketFactory)
				.buildConfiguration());
		bot.startBot();

		//Make sure the bot is connected
		verify(socketFactory).createSocket(localhost, 6667, null, 0);

		//Setup useful vars
		aUser = bot.getUserChannelDao().getUser("aUser");
		aChannel = bot.getUserChannelDao().getChannel("#aChannel");
	}

	@AfterMethod
	public void cleanUp() {
		inputLatch.countDown();
	}

	@Test(description = "Verify sendRawLine works correctly")
	public void sendRawLineTest() throws Exception {
		bot.sendRaw().rawLine(aString);
		checkOutput(aString);
	}

	@Test(description = "Verify sendRawLineNow works correctly")
	public void sendRawLineNowTest() throws Exception {
		bot.sendRaw().rawLineNow(aString);
		checkOutput(aString);
	}

	@Test(description = "Verify sendRawLineSplit works correctly with short strings")
	public void sendRawLineSplitShort() throws Exception {
		String beginning = "BEGIN";
		String ending = "END";
		bot.sendRaw().rawLineSplit(beginning, aString, ending);
		checkOutput(beginning + aString + ending);
	}

	@Test(description = "Verify sendRawLineSplit works correctly with long strings")
	public void sendRawLineSplitLong() throws Exception {
		//Generate string parts
		String beginning = "BEGIN";
		String ending = "END";

		//Build a randomly generated seed string
		Random random = new Random();
		int botMaxLineLength = bot.getConfiguration().getMaxLineLength();
		StringBuilder seedStringBuilder = new StringBuilder(128);
		seedStringBuilder.append(" - ");
		while ((beginning.length() + "1".length() + seedStringBuilder.length() + ending.length()) < botMaxLineLength - 2)
			seedStringBuilder.append((char) (random.nextInt(26) + 'a'));
		String seedString = seedStringBuilder.toString();
		String[] stringParts = new String[]{
			"1" + seedString,
			"2" + seedString,
			"3" + seedString.substring(0, botMaxLineLength / 2)
		};

		//Send the message, joining all the message parts into one big chunck
		bot.sendRaw().rawLineSplit(beginning, StringUtils.join(stringParts, ""), ending);

		//Verify sent lines, making sure they come out in parts
		Iterator<String> outputItr = checkOutput(beginning + stringParts[0] + ending);
		//Verify further lines
		assertEquals(tryGetNextLine(outputItr), beginning + stringParts[1] + ending, "Second string part doesn't match");
		assertEquals(tryGetNextLine(outputItr), beginning + stringParts[2] + ending, "Third string part doesn't match");
	}

	@Test(description = "Verify sendAction to user")
	public void sendActionUserTest() throws Exception {
		aUser.send().action(aString);
		checkOutput("PRIVMSG aUser :\u0001ACTION " + aString + "\u0001");
	}

	@Test(description = "Verify sendAction to channel")
	public void sendActionChannelTest() throws Exception {
		aChannel.send().action(aString);
		checkOutput("PRIVMSG #aChannel :\u0001ACTION " + aString + "\u0001");
	}

	@Test(description = "Verify sendAction by string")
	public void sendActionStringTest() throws Exception {
		bot.sendIRC().action("#aChannel", aString);
		checkOutput("PRIVMSG #aChannel :\u0001ACTION " + aString + "\u0001");
	}

	@Test(description = "Verify sendCTCPCommand to user")
	public void sendCTCPCommandUserTest() throws Exception {
		aUser.send().ctcpCommand(aString);
		checkOutput("PRIVMSG aUser :\u0001" + aString + "\u0001");
	}

	@Test(description = "Verify sendCTCPCommand to channel")
	public void sendCTCPCommandChannelTest() throws Exception {
		aChannel.send().ctcpCommand(aString);
		checkOutput("PRIVMSG #aChannel :\u0001" + aString + "\u0001");
	}

	@Test(description = "Verify sendCTCPCommand by string")
	public void sendCTCPCommandStringTest() throws Exception {
		bot.sendIRC().ctcpCommand("#aChannel", aString);
		checkOutput("PRIVMSG #aChannel :\u0001" + aString + "\u0001");
	}

	@Test(description = "Verify sendCTCPResponse to user")
	public void sendCTCPResponseUserTest() throws Exception {
		aUser.send().ctcpResponse(aString);
		checkOutput("NOTICE aUser :\u0001" + aString + "\u0001");
	}

	@Test(description = "Verify sendCTCPResponse by string")
	public void sendCTCPResponseStringTest() throws Exception {
		bot.sendIRC().ctcpResponse("aUser", aString);
		checkOutput("NOTICE aUser :\u0001" + aString + "\u0001");
	}

	@Test(description = "Verify sendInvite to user")
	public void sendInviteUserChannelTest() throws Exception {
		aUser.send().invite(aChannel);
		checkOutput("INVITE aUser :#aChannel");
	}

	@Test(description = "Verify sendInvite to channel")
	public void sendInviteChannelChannelTest() throws Exception {
		aChannel.send().invite(bot.getUserChannelDao().getChannel("#otherChannel"));
		checkOutput("INVITE #aChannel :#otherChannel");
	}

	@Test(description = "Verify sendInvite to channel by string")
	public void sendInviteChannelStringlTest() throws Exception {
		aUser.send().invite("#aChannel");
		checkOutput("INVITE aUser :#aChannel");
	}

	@Test(description = "Verify sendInvite by string")
	public void sendInviteStringlTest() throws Exception {
		bot.sendIRC().invite("aUser", "#aChannel");
		checkOutput("INVITE aUser :#aChannel");
	}

	@Test(description = "Verify sendMessage to channel")
	public void sendMessageChannelTest() throws Exception {
		aChannel.send().message(aString);
		checkOutput("PRIVMSG #aChannel :" + aString);
	}

	@Test(description = "Verify sendMessage to user in channel")
	public void sendMessageChannelUserTest() throws Exception {
		aChannel.send().message(aUser, aString);
		checkOutput("PRIVMSG #aChannel :aUser: " + aString);
	}

	@Test(description = "Verify sendMessage to user")
	public void sendMessageUserTest() throws Exception {
		aUser.send().message(aString);
		checkOutput("PRIVMSG aUser :" + aString);
	}

	@Test(description = "Verify sendMessage by string")
	public void sendMessageStringTest() throws Exception {
		bot.sendIRC().message("aUser", aString);
		checkOutput("PRIVMSG aUser :" + aString);
	}

	@Test(description = "Verify sendNotice to channel")
	public void sendNoticeChannelTest() throws Exception {
		aChannel.send().notice(aString);
		checkOutput("NOTICE #aChannel :" + aString);
	}

	@Test(description = "Verify sendNotice to user")
	public void sendNoticeUserTest() throws Exception {
		aUser.send().notice(aString);
		checkOutput("NOTICE aUser :" + aString);
	}

	@Test(description = "Verify sendNotice by String")
	public void sendNoticeStringTest() throws Exception {
		bot.sendIRC().notice("aUser", aString);
		checkOutput("NOTICE aUser :" + aString);
	}

	@Test
	public void sendQuit() throws Exception {
		bot.sendIRC().quitServer();
		checkOutput("QUIT :");
	}

	@Test
	public void sendQuitMessage() throws Exception {
		bot.sendIRC().quitServer(aString);
		checkOutput("QUIT :" + aString);
	}

	/**
	 * Check the output for one line that equals the expected value.
	 * @param expected
	 */
	protected Iterator<String> checkOutput(String expected) throws IOException {
		List<String> outputLines = Arrays.asList(StringUtils.split(botOut.toString(), "\n\r"));
		Iterator<String> outputItr = outputLines.iterator();
		//Handle the first 3 lines from the bot
		assertEquals(tryGetNextLine(outputItr), "CAP LS", "Unexpected first line");
		assertEquals(tryGetNextLine(outputItr), "NICK PircBotXBot", "Unexecpted second line");
		assertTrue(tryGetNextLine(outputItr).startsWith("USER PircBotX 8 * :"), "Unexpected third line");

		//Make sure the remaining line is okay
		assertEquals(tryGetNextLine(outputItr), expected);
		//assertEquals(lines.length, 1, "Too many/few lines: " + StringUtils.join(lines, System.getProperty("line.separator")));

		return outputItr;
	}

	protected static String tryGetNextLine(Iterator<String> itr) {
		assertTrue(itr.hasNext(), "No more lines to get");
		return itr.next();
	}
}
