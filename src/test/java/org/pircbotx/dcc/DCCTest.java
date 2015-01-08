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
package org.pircbotx.dcc;

import org.testng.annotations.DataProvider;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.pircbotx.PircBotX;
import org.pircbotx.TestUtils;
import org.pircbotx.dcc.DccHandler;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.IncomingChatRequestEvent;
import org.pircbotx.hooks.managers.GenericListenerManager;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 *
 * @author Leon Blakey
 */
public class DCCTest {
	PircBotX bot;

	@BeforeClass(description = "This will fail if you don't have IPv6 support")
	public void checkIPv6Support() throws UnknownHostException {
		InetAddress.getByName("::1");
	}

	@BeforeMethod
	public void setup() throws UnknownHostException {
		bot = new PircBotX(TestUtils.generateConfigurationBuilder()
				.setLocalAddress(InetAddress.getByName("::1"))
				.buildConfiguration());
	}

	@Test
	public void incommingDccChatTest() throws IOException, InterruptedException, IrcException {
		Inet6Address localhost6 = (Inet6Address) InetAddress.getByName("::1");

		//Create listener to handle everything
		final MutableObject<IncomingChatRequestEvent> mutableEvent = new MutableObject<IncomingChatRequestEvent>();
		bot.getConfiguration().getListenerManager().addListener(new ListenerAdapter() {
			@Override
			public void onIncomingChatRequest(IncomingChatRequestEvent event) throws Exception {
				mutableEvent.setValue(event);
			}
		});

		System.out.println("localhost6 byte array: " + Arrays.toString(localhost6.getAddress()));
		System.out.println("localhost6 int: " + new BigInteger(localhost6.getAddress()));

		bot.getInputParser().handleLine(":ANick!~ALogin@::2 PRIVMSG Quackbot5 :DCC CHAT chat 1 35589");
		IncomingChatRequestEvent event = mutableEvent.getValue();
		assertNotNull(event, "No IncomingChatRequestEvent dispatched");

		System.out.println("Test ran");
	}

	@Test(dataProvider = "addressDataProvider")
	public void addressToIntegerTest(String rawAddress, String expectedResult) throws UnknownHostException {
		InetAddress address = InetAddress.getByName(rawAddress);
		String convertedAddress = DccHandler.addressToInteger(address);
		assertEquals(convertedAddress, expectedResult, "Converted address doesn't match given");
	}

	@Test(dataProvider = "addressDataProvider")
	public void integerToAddressTest(String rawAddress, String integerAddress) throws UnknownHostException {
		InetAddress realAddress = InetAddress.getByName(rawAddress);
		InetAddress convertedAddress = DccHandler.parseRawAddress(integerAddress);
		assertEquals(convertedAddress, realAddress, "Converted address doesn't match given");
	}

	@DataProvider
	public Object[][] addressDataProvider() {
		//Note: All numbers are verified with another tool
		return new Object[][]{
			//IPv4 Tests	
			{"127.0.0.1", "2130706433"},
			{"192.168.21.6", "3232240902"},
			{"75.221.45.21", "1272786197"},
			//IPv6 tests (Just make sure we get back what we put in)
			{"2001:0db8:85a3:0000:0000:8a2e:0370:7334", "2001:db8:85a3:0:0:8a2e:370:7334"},
			{"fe80:0:0:0:202:b3ff:fe1e:8329", "fe80:0:0:0:202:b3ff:fe1e:8329"},
			{"fe80::202:b3ff:fe1e:5329", "fe80:0:0:0:202:b3ff:fe1e:5329"},
			//IPv6 tests (Switch to returning full ip in 2.1) 
			//{"2001:0db8:85a3:0000:0000:8a2e:0370:7334", "42540766452641154071740215577757643572"},
			//{"fe80:0:0:0:202:b3ff:fe1e:8329", "338288524927261089654163772891438416681"},
			//{"fe80::202:b3ff:fe1e:5329", "338288524927261089654163772891438404393"},};
		};
	}

	protected void debug(String type, InetAddress address) {
		System.out.println(type + ": " + address + " | Class: " + address.getClass());
	}
}
