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
package org.pircbotx;

import org.pircbotx.hooks.events.ConnectEvent;
import java.util.List;
import org.pircbotx.hooks.Event;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import javax.net.SocketFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.pircbotx.hooks.managers.GenericListenerManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.mockito.Mockito.*;
import org.pircbotx.cap.EnableCapHandler;
import org.pircbotx.hooks.Listener;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.SocketConnectEvent;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;

/**
 * Do various connect tests. Note that this is in a separate class since PircBotXOutputTest
 * relies on a working mock implementation
 * @author Leon Blakey <lord.quackstar at gmail.com>
 */
@Test(groups = "ConnectTests", singleThreaded = true)
public class PircBotXConnectTest {
	protected Configuration.Builder configurationBuilder;
	protected SocketFactory socketFactory;
	protected Socket socket;
	protected ByteArrayInputStream botIn;
	protected ByteArrayOutputStream botOut;
	protected List<Event> events;
	protected InetAddress address;

	@BeforeClass
	public void setUp() throws UnknownHostException {
		address = InetAddress.getLocalHost();
	}

	@BeforeMethod
	public void botProvider() throws Exception {
		//Setup stream
		botIn = new ByteArrayInputStream(":ircd.test CAP * LS :sasl\r\n:ircd.test 004 PircBotXUser ircd.test jmeter-ircd-basic-0.1 ov b\r\n".getBytes());
		botOut = new ByteArrayOutputStream();
		socket = mock(Socket.class);
		when(socket.isConnected()).thenReturn(true);
		when(socket.getInputStream()).thenReturn(botIn);
		when(socket.getOutputStream()).thenReturn(botOut);
		socketFactory = mock(SocketFactory.class);
		when(socketFactory.createSocket(address, 6667, null, 0)).thenReturn(socket);

		//Setup bot
		events = new ArrayList<Event>();
		configurationBuilder = TestUtils.generateConfigurationBuilder()
				.addListener(new Listener() {
			public void onEvent(Event event) throws Exception {
				LoggerFactory.getLogger(getClass()).debug("Called");
				events.add(event);
			}
		})
				.setName("PircBotXBot");
	}

	protected void validateEvents(PircBotX bot) throws Exception {
		Event event = events.get(0);
		assertTrue(event instanceof SocketConnectEvent, "Unknown first event: " + event);
		assertEquals(event.getBot(), bot);

		event = events.get(2);
		assertTrue(event instanceof ConnectEvent, "Unknown third event: " + event);
		assertEquals(event.getBot(), bot);
		
		event = events.get(4);
		assertTrue(event instanceof DisconnectEvent, "Unknown fifth event: " + event);
		assertEquals(event.getBot(), bot);
	}

	@Test
	public void connectTest() throws Exception {
		//Connect the bot to the socket
		PircBotX bot = new PircBotX(configurationBuilder
				.setServer(address.getHostName(), 6667)
				.setServerPassword(null)
				.setSocketFactory(socketFactory)
				.buildConfiguration());
		bot.connect();

		//Make sure the bot is connected
		verify(socketFactory).createSocket(address, 6667, null, 0);

		//Verify lines
		String[] lines = botOut.toString().split("\r\n");

		assertEquals(lines.length, 4, "Extra line: " + StringUtils.join(lines, SystemUtils.LINE_SEPARATOR));

		assertEquals(lines[0], "CAP LS");
		assertEquals(lines[1], "NICK PircBotXBot");
		assertEquals(lines[2], "USER " + configurationBuilder.getLogin() + " 8 * :" + configurationBuilder.getVersion());
		assertEquals(lines[3], "CAP END");

		validateEvents(bot);
	}

	@Test(dependsOnMethods = "connectTest")
	public void connectWithDifferentPortTest() throws Exception {
		//Connect the bot to the socket
		when(socketFactory.createSocket(address, 25622, null, 0)).thenReturn(socket);
		PircBotX bot = new PircBotX(configurationBuilder
				.setServer(address.getHostName(), 25622)
				.setServerPassword(null)
				.setSocketFactory(socketFactory)
				.buildConfiguration());
		bot.connect();

		//Make sure the bot is connected
		verify(socketFactory).createSocket(address, 25622, null, 0);

		//Verify lines
		String[] lines = botOut.toString().split("\r\n");

		assertEquals(lines.length, 4, "Extra line: " + StringUtils.join(lines, SystemUtils.LINE_SEPARATOR));

		assertEquals(lines[0], "CAP LS");
		assertEquals(lines[1], "NICK PircBotXBot");
		assertEquals(lines[2], "USER " + configurationBuilder.getLogin() + " 8 * :" + configurationBuilder.getVersion());
		assertEquals(lines[3], "CAP END");

		validateEvents(bot);
	}

	@Test(dependsOnMethods = "connectTest")
	public void connectWithPasswordTest() throws Exception {
		//Connect the bot to the socket
		PircBotX bot = new PircBotX(configurationBuilder
				.setServer(address.getHostName(), 6667)
				.setServerPassword("pa55w0rd")
				.setSocketFactory(socketFactory)
				.buildConfiguration());
		bot.connect();

		//Make sure the bot is connected
		verify(socketFactory).createSocket(address, 6667, null, 0);

		//Verify lines
		String[] lines = botOut.toString().split("\r\n");

		assertEquals(lines.length, 5, "Extra line: " + StringUtils.join(lines, SystemUtils.LINE_SEPARATOR));

		assertEquals(lines[0], "CAP LS");
		assertEquals(lines[1], "PASS pa55w0rd");
		assertEquals(lines[2], "NICK PircBotXBot");
		assertEquals(lines[3], "USER " + configurationBuilder.getLogin() + " 8 * :" + configurationBuilder.getVersion());
		assertEquals(lines[4], "CAP END");

		validateEvents(bot);
	}

	@Test(dependsOnMethods = "connectTest")
	public void connectTestWithUnknownCap() throws Exception {
		configurationBuilder.getCapHandlers().clear();
		configurationBuilder.addCapHandler(new EnableCapHandler("jdshflkashfalksjh", true));

		//Connect the bot to the socket
		PircBotX bot = new PircBotX(configurationBuilder
				.setServer(address.getHostName(), 6667)
				.setServerPassword(null)
				.setSocketFactory(socketFactory)
				.buildConfiguration());
		bot.connect();

		//Make sure the bot is connected
		verify(socketFactory).createSocket(address, 6667, null, 0);

		//Verify lines
		String[] lines = botOut.toString().split("\r\n");

		assertEquals(lines.length, 4, "Extra line: " + StringUtils.join(lines, SystemUtils.LINE_SEPARATOR));

		assertEquals(lines[0], "CAP LS");
		assertEquals(lines[1], "NICK PircBotXBot");
		assertEquals(lines[2], "USER " + configurationBuilder.getLogin() + " 8 * :" + configurationBuilder.getVersion());
		assertEquals(lines[3], "CAP END");

		validateEvents(bot);
	}
}
