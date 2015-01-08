/**
 * Copyright (C) 2010-2014 Leon Blakey <lord.quackstar at gmail.com>
 *
 * This file is part of PircBotX.
 *
 * PircBotX is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * PircBotX is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * PircBotX. If not, see <http://www.gnu.org/licenses/>.
 */
package org.pircbotx;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.net.SocketFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableObject;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import org.pircbotx.cap.CapHandler;
import org.pircbotx.cap.EnableCapHandler;
import org.pircbotx.cap.SASLCapHandler;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author Leon Blakey
 */
@Slf4j
public class CAPTest {
	protected final int EOL = Character.getNumericValue('\n');
	protected InputStream botIn;
	protected PipedOutputStream botInWrite;
	protected OutputStream botOut;
	protected final List<CapHandler> capHandlers = new ArrayList<CapHandler>();
	protected PircBotX bot;

	@BeforeMethod
	public void resetCapHandlers() {
		capHandlers.clear();
	}

	public void runTest(String cap, final OutputParser callback) throws Exception {
		final MutableObject<Exception> connectionException = new MutableObject<Exception>();
		botInWrite = new PipedOutputStream();
		botIn = new BufferedInputStream(new PipedInputStream(botInWrite));
		botOut = new ByteArrayOutputStream() {
			@Override
			public synchronized void write(byte[] bytes, int i, int i1) {
				super.write(bytes, i, i1);
				String outputText = new String(bytes, i, i1).trim();

				try {
					try {
						String callbackText = callback.handleOutput(outputText);
						if (callbackText == null)
							//Will close bots input loop
							botInWrite.close();
						else if (!callbackText.equals("")) {
							botInWrite.write((callbackText + "\r\n").getBytes());
							botInWrite.flush();
						}
					} catch (Exception ex) {
						log.error("Recieved error, closing bot and escelating", ex);
						connectionException.setValue(ex);
						botInWrite.close();
					}
				} catch (IOException ex) {
					log.error("Recieved IO error, closing bot and escelating", ex);
					connectionException.setValue(ex);
					try {
						botInWrite.close();
					} catch (Exception e) {
						throw new RuntimeException("Can't close botInWrite", e);
					}
				}
			}
		};
		Socket socket = mock(Socket.class);
		when(socket.isConnected()).thenReturn(true);
		when(socket.getInputStream()).thenReturn(botIn);
		when(socket.getOutputStream()).thenReturn(botOut);
		
		Configuration.Builder configurationBuilder = TestUtils.generateConfigurationBuilder();
		
		SocketFactory socketFactory = mock(SocketFactory.class);
		when(socketFactory.createSocket(InetAddress.getByName(configurationBuilder.getServers().get(0).getHostname()), 6667, null, 0))
				.thenReturn(socket);

		configurationBuilder.getCapHandlers().clear();
		configurationBuilder.getCapHandlers().addAll(capHandlers);
		bot = new PircBotX(configurationBuilder
				.setSocketFactory(socketFactory)
				.setAutoReconnect(false)
				.buildConfiguration());

		botInWrite.write((":ircd.test CAP * LS :" + cap + "\r\n").getBytes());
		bot.connect();
		if (connectionException.getValue() != null)
			throw connectionException.getValue();
	}

	protected static interface OutputParser {
		public String handleOutput(String output) throws Exception;
	}

	@Test
	public void SASLTest() throws Exception {
		capHandlers.add(new SASLCapHandler("jilles", "sesame"));
		runTest("sasl", new OutputParser() {
			public String handleOutput(String output) throws Exception {
				if (output.equals("CAP REQ :sasl"))
					return ":ircd.test CAP * ACK :sasl";
				else if (output.equals("AUTHENTICATE PLAIN"))
					return "AUTHENTICATE +";
				else if (output.equals("AUTHENTICATE amlsbGVzAGppbGxlcwBzZXNhbWU="))
					//Done
					return null;
				else if (output.startsWith("AUTHENTICATE"))
					//Unknown AUTHENTICATE, most likely an invalid username/password
					throw new RuntimeException("Unknown or invalid SASL auth: " + output);
				return "";
			}
		});
		assertTrue(bot.getEnabledCapabilities().contains("sasl"), "SASL isn't on the enabled capabilities list");
	}

	@Test
	public void EnableTest() throws Exception {
		capHandlers.add(new EnableCapHandler("test-cap", true));
		runTest("test-cap", new OutputParser() {
			boolean acked = false;

			public String handleOutput(String output) throws Exception {
				if (output.equals("CAP REQ :test-cap")) {
					acked = true;
					return ":ircd.test CAP * ACK :test-cap";
				} else if (output.equals("CAP END"))
					//Done
					return null;
				return "";

			}
		});
		assertTrue(bot.getEnabledCapabilities().contains("test-cap"), "SASL isn't on the enabled capabilities list");
	}
}
