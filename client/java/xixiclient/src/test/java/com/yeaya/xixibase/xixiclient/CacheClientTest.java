package com.yeaya.xixibase.xixiclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.lang.Integer;

import com.yeaya.xixibase.xixiclient.util.CurrentTick;
import com.yeaya.xixibase.xixiclient.util.Log;

import junit.framework.TestCase;

public class CacheClientTest extends TestCase {
	private static final String managerName1 = "manager1";
	private static CacheClientManager mgr1 = null;
	private static CacheClient cc1 = null;
	
	static String servers;
	static String[] serverlist;
	static boolean enableSSL = false;
	static {
		servers = System.getProperty("hosts");
		enableSSL = System.getProperty("enableSSL") != null && System.getProperty("enableSSL").equals("true");
		if (servers == null) {
			try {
				InputStream in = CacheClientTest.class.getResourceAsStream("/test.properties");
				Properties p = new Properties(); 
				p.load(in);
				in.close();
				servers = p.getProperty("hosts");
				enableSSL = p.getProperty("enableSSL") != null && p.getProperty("enableSSL").equals("true");
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}
		serverlist = servers.split(",");

		mgr1 = CacheClientManager.getInstance(managerName1);
		mgr1.setSocketWriteBufferSize(64 * 1024);
		mgr1.initialize(serverlist, enableSSL);
		mgr1.enableLocalCache();
	}

	protected void setUp() throws Exception {
		super.setUp();
		CacheClientManager mgr1 = CacheClientManager.getInstance(managerName1);
		cc1 = mgr1.createClient();
		Log.enableLog4j();
		Log.setLevel(0);
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		assertNotNull(cc1);
		cc1.flush();
		Log.disableLog4j();
	}

	public void testFlush() {
		CacheClient cca = mgr1.createClient(3);
		CacheClient ccb = mgr1.createClient(15);
		cca.set("xixi1", "0315");
		ccb.set("xixi2", "20080315");
		int count = cca.flush();
		assertEquals(1, count);
		count = ccb.flush();
		assertEquals(1, count);
		assertFalse(cca.keyExists("xixi1"));
		assertFalse(ccb.keyExists("xixi2"));
	}
	
	public void testFlush2() {
		assertTrue(serverlist.length > 1);
		ArrayList<String> keys = new ArrayList<String>();
		ArrayList<String> values = new ArrayList<String>();
		for (int i = 0; i < 100; i++) {
			String key = "xixi" + i;
			String value = "value" + i;
			keys.add(key);
			values.add(value);
			cc1.set(key, value);
		}
		int lastCount = 0;
		for (int i = 0; i < serverlist.length; i++) {
			String[] sl = new String[1];
			sl[0] = serverlist[i];
			int flushCount = cc1.flush(sl);
			int count = 0;
			for (int j = 0; j < 100; j++) {
				if (cc1.get("xixi" + j) == null) {
					count++;				
				}
			}
			assertEquals(flushCount, count - lastCount);
			assertTrue(count > 0);
			assertTrue(count > lastCount);
			lastCount = count;
		}
		assertEquals(100, lastCount);
	}
	
	public void testFlushError() {
		String[] svrList = new String[0];
		cc1.set("xixi", "0315");
		assertEquals(0, cc1.flush(svrList));
		
		mgr1.shutdown();
		cc1.flush();
		
		mgr1 = CacheClientManager.getInstance(managerName1);
		mgr1.setSocketWriteBufferSize(64 * 1024);
		mgr1.initialize(serverlist, enableSSL);
		mgr1.enableLocalCache();
	}
	
	public void testGetError() {
		assertNull(cc1.get(null));
		long ret = cc1.set("xixi", "value");
		assertTrue(ret != 0);
		assertEquals("value", cc1.get("xixi"));
		assertNull(cc1.get("xixi2"));
		String cn = cc1.getTransCoder().getEncodingCharsetName();
		boolean isSanitizeKeys = cc1.getTransCoder().isSanitizeKeys(); 
		cc1.getTransCoder().setSanitizeKeys(true);
		cc1.getTransCoder().setEncodingCharsetName("errorCharsetName");
		assertNull(cc1.get("xixi"));
		cc1.getTransCoder().setSanitizeKeys(isSanitizeKeys);
		mgr1.shutdown();
		cc1.getTransCoder().setEncodingCharsetName(cn);
		assertNull(cc1.get("xixi"));
		assertNull(cc1.getL("xixi"));
		
		mgr1 = CacheClientManager.getInstance(managerName1);
		mgr1.setSocketWriteBufferSize(64 * 1024);
		mgr1.initialize(serverlist, enableSSL);
		mgr1.enableLocalCache();
	}
	
	public void testGroup() {
		CacheClient cca = mgr1.createClient(3);
		CacheClient ccb = mgr1.createClient(15);
	//	int groupID = cc1.getGroupID();
//		cc1.setGroupID(3);
		cca.set("xixi", "group3");
//		ccb.setGroupID(15);
		ccb.set("xixi", "group15");

	//	cc1.setGroupID(3);
		assertEquals("group3", cca.get("xixi"));
	//	cc1.setGroupID(15);
		assertEquals("group15", ccb.get("xixi"));
		
//		cc1.setGroupID(3);
		int count = cca.flush();
		assertEquals(1, count);
//		cc1.setGroupID(15);
		count = ccb.flush();
		assertEquals(1, count);
		
//		cc1.setGroupID(groupID);
		
		assertNull(cca.getLastError());
		assertNull(ccb.getLastError());
	}

	public void testObjectTransCoderEncode() {
		int myInt = 0x12345678;
		byte[] b = ObjectTransCoder.encodeInt(myInt);
		int intValue = ObjectTransCoder.decodeInt(b);
		assertEquals(intValue, myInt);
		
		long myLong = 0x1234567890123456L;
		b = ObjectTransCoder.encodeLong(myLong);
		long longValue = ObjectTransCoder.decodeLong(b);
		assertEquals(longValue, myLong);
	}
	
	public void testTransCoder() {
		TransCoder oldCoder = cc1.getTransCoder();
		TransCoder coder = new ObjectTransCoder() {
			@Override
			public byte[] encode(final Object object, int[] flags, int[]/*out*/ objectSize) throws IOException {
				return super.encode(object, flags, objectSize);
			}
		};
		cc1.setTransCoder(coder);
		cc1.set("xixi", "testTransCoder");
		assertEquals("testTransCoder", cc1.get("xixi"));
		cc1.setTransCoder(oldCoder);
	}

	public void testDelete() {
		cc1.set("xixi", Boolean.TRUE);
		Boolean b = (Boolean) cc1.get("xixi");
		assertEquals(b.booleanValue(), true);
		cc1.delete("xixi");
		assertEquals(null, cc1.get("xixi"));
		assertFalse(cc1.delete(null));
	}

	public void testDelete2() {
		cc1.set("xixi", new Long(2008));
		CacheItem item = cc1.gets("xixi");
		assertEquals(item.getValue(), new Long(2008));
		assertTrue(item.cacheID != 0);
		boolean ret = cc1.delete("xixi", item.cacheID + 1);
		assertFalse(ret);
		ret = cc1.delete("xixi", item.cacheID);
		assertTrue(ret);
		assertEquals(null, cc1.get("xixi"));
		assertFalse(cc1.delete(null));
	}
	
	public void testSetBoolean() {
		cc1.set("xixi", Boolean.TRUE);
		Boolean b = (Boolean) cc1.get("xixi");
		assertEquals(b.booleanValue(), true);
		
		cc1.set("xixi", Boolean.FALSE);
		b = (Boolean) cc1.get("xixi");
		assertEquals(b.booleanValue(), false);
	}

	public void testSetIntegers() {
		int[] value = new int[5];
		for (int i = 0; i < value.length; i++) {
			value[i] = i;
		}
		cc1.set("xixi", value);
		int[] value2 = (int[]) cc1.get("xixi");
		assertEquals(value.length, value2.length);
		for (int i = 0; i < value.length; i++) {
			assertEquals(value[i], value2[i]);
		}
	}
	
	public void testSetInteger() {
		cc1.set("xixi", new Integer(Integer.MAX_VALUE));
		Integer i = (Integer) cc1.get("xixi");
		assertEquals(i.intValue(), Integer.MAX_VALUE);
	}
	
	public void testSetDate() {
		Date d1 = new Date();
		cc1.set("xixi", d1);
		Date d2 = (Date) cc1.get("xixi");
		assertEquals(d1, d2);
	}
	
	public void testSetString() {
		String value = "test of string encoding";
		CacheClientManager mgr = CacheClientManager.getInstance("test1");
		mgr.setNoDelay(false);
		mgr.initialize(serverlist, null,
				new XixiWeightMap<Integer>(false, XixiWeightMap.CRC32_HASH),
				enableSSL);
		CacheClient cc = mgr.createClient();
		cc.set("xixi", value);
		String s = (String) cc.get("xixi");
		assertEquals(s, value);
		cc.set("xixi1", value);
		s = (String) cc.get("xixi1");
		assertEquals(s, value);
		mgr.shutdown();
		mgr = CacheClientManager.getInstance("test2");
		mgr.setNoDelay(true);
		mgr.initialize(serverlist, null,
				new XixiWeightMap<Integer>(false, XixiWeightMap.MD5_HASH),
				enableSSL);
		cc = mgr.createClient();
		cc.set("xixi", value);
		s = (String) cc.get("xixi");
		assertEquals(s, value);
		cc.set("xixi2", value);
		s = (String) cc.get("xixi2");
		assertEquals(s, value);
		mgr.shutdown();
	}
	
	public void testCacheItem() {
		long cacheID = cc1.add("xixi", "ke-ai");
		assertTrue(cacheID != 0);
		CacheItem item = cc1.gets("xixi");
		assertEquals(item.getCacheID(), cacheID);
		long time = item.getExpiration();
		assertEquals(0, time);
		assertTrue(item.getFlags() > 0);
		assertEquals(item.getGroupID(), 0);
		assertEquals(item.getOption1(), 0);
		assertEquals(item.getOption2(), 0);
		assertEquals(item.getValue(), "ke-ai");
	}
	
	public void testGetBase() {
		cc1.getTransCoder().setOption1((short)0x1234);
		cc1.getTransCoder().setOption2((byte)0xF1);
		cc1.set("xixi", "base");
		CacheBaseItem item = cc1.getBase("xixi");
		assertTrue(item.cacheID != 0);
		assertEquals((short)0x1234, item.getOption1());
		assertEquals((byte)0xF1, item.getOption2());
		assertEquals((short)0x1234, cc1.getTransCoder().getOption1());
		assertEquals((byte)0xF1, cc1.getTransCoder().getOption2());
		cc1.delete("xixi");
		assertNull(cc1.getBase("xixi"));
	}
	
	public void testGetBaseError() {
		cc1.set("xixi", "base");
		CacheBaseItem item = cc1.getBase(null);
		assertNull(item);
		boolean isSanitizeKeys = cc1.getTransCoder().isSanitizeKeys();
		String cn = cc1.getTransCoder().getEncodingCharsetName();
		cc1.getTransCoder().setSanitizeKeys(true);
		cc1.getTransCoder().setEncodingCharsetName("encodingCharsetName");
		item = cc1.getBase("xixi");
		assertNull(item);
		
		cc1.getTransCoder().setSanitizeKeys(isSanitizeKeys);
		cc1.getTransCoder().setEncodingCharsetName(cn);
		
		mgr1.shutdown();
		
		item = cc1.getBase("xixi");
		assertNull(item);
		
		mgr1 = CacheClientManager.getInstance(managerName1);
		mgr1.setSocketWriteBufferSize(64 * 1024);
		mgr1.initialize(serverlist, enableSSL);
		mgr1.enableLocalCache();
	}
	
	public void testKeyExsits() {
		cc1.set("xixi", "base");
		assertTrue(cc1.keyExists("xixi"));
		cc1.delete("xixi");
		assertFalse(cc1.keyExists("xixi"));
	}

	public void testWeightMap() {
		String input = "test of string encoding";

		CacheClientManager mgr = CacheClientManager.getInstance("testWeightMap");
		mgr.setNoDelay(false);
		mgr.initialize(serverlist, null,
				new XixiWeightMap<Integer>(false, XixiWeightMap.CRC32_HASH),
				enableSSL);
		CacheClient cc = mgr.createClient();
		assertFalse(mgr.getWeightMapper().isConsistent());
		assertEquals(XixiWeightMap.CRC32_HASH, mgr.getWeightMapper().getHashingAlg());
		
		cc.set("xixi", input);
		String s = (String) cc.get("xixi");
		assertEquals(s, input);
		cc.set("xixi1", input);
		s = (String) cc.get("xixi1");
		assertEquals(s, input);
		cc.flush();
		mgr.shutdown();
	}
	
	public void testWeightMap2() {
		String input = "test of string encoding";
		CacheClientManager mgr = CacheClientManager.getInstance("testWeightMap2");
		mgr.setNoDelay(false);
		Integer[] weights = new Integer[2];
		weights[0] = Integer.valueOf(0);
		weights[1] = Integer.valueOf(118);
		mgr.initialize(serverlist, weights,
				new XixiWeightMap<Integer>(true, XixiWeightMap.NATIVE_HASH),
				enableSSL);
		CacheClient cc = mgr.createClient();
		cc.set("xixi", input);
		String s = (String) cc.get("xixi");
		assertEquals(s, input);
		cc.set("xixi2", input, 10);
		s = (String) cc.get("xixi2");
		assertEquals(s, input);
		mgr.shutdown();
		
		mgr = CacheClientManager.getInstance("test3");
		mgr.setNoDelay(false);
		weights = new Integer[2];
		weights[0] = Integer.valueOf(1);
		weights[1] = Integer.valueOf(8);
		mgr.initialize(serverlist, weights,
				new XixiWeightMap<Integer>(true, XixiWeightMap.MD5_HASH),
				enableSSL);
		cc = mgr.createClient();
		cc.set("xixi", input);
		s = (String) cc.get("xixi");
		assertEquals(s, input);
		cc.set("xixi2", input, 10);
		s = (String) cc.get("xixi2");
		assertEquals(s, input);
		cc.flush();
		mgr.shutdown();
	}

	public void testSetChar() {
		cc1.set("xixi", Character.valueOf('Y'));
		Character c = (Character) cc1.get("xixi");
		assertEquals(c.charValue(), 'Y');
	}

	public void testSetByte() {
		cc1.set("xixi", Byte.valueOf((byte) 127));
		Byte b = (Byte) cc1.get("xixi");
		assertEquals(b.byteValue(), (byte) 127);
	}

	public void testSetStringBuffer() {
		cc1.set("xixi", new StringBuffer("0315"));
		StringBuffer o = (StringBuffer) cc1.get("xixi");
		assertEquals(o.toString(), "0315");
	}
	
	public void testSetStringBuilder() {
		cc1.set("xixi", new StringBuilder("0315"));
		StringBuilder o = (StringBuilder) cc1.get("xixi");
		assertEquals(o.toString(), "0315");
	}
	
	public void testGetCacheItem() {
		cc1.set("xixi", new StringBuilder("0315"));
		CacheItem item = cc1.gets("xixi");
		assertNotNull(item);
		assertTrue(item.cacheID != 0);
		assertEquals(item.value.toString(), "0315");
	}

	public void testSetShort() {
		cc1.set("xixi", Short.valueOf((short) 100));
		Short o = (Short) cc1.get("xixi");
		assertEquals(o.shortValue(), (short) 100);
	}

	public void testSetLong() {
		cc1.set("xixi", Long.valueOf(Long.MAX_VALUE));
		Long o = (Long) cc1.get("xixi");
		assertEquals(o.longValue(), Long.MAX_VALUE);
	}

	public void testSetDouble() {
		cc1.set("xixi", new Double(3.15));
		Double o = (Double) cc1.get("xixi");
		assertEquals(o.doubleValue(), 3.15);
	}

	public void testSetFloat() {
		cc1.set("xixi", new Float(3.15f));
		Float o = (Float) cc1.get("xixi");
		assertEquals(o.floatValue(), 3.15f);
	}

	public void testSetByteArray() {
		byte[] b = new byte[10];
		for (int i = 0; i < 10; i++)
			b[i] = (byte) i;

		cc1.set("xixi", b);
		assertTrue(Arrays.equals((byte[]) cc1.get("xixi"), b));
	}

	public void testSetObj() {
		SerialItem item = new SerialItem("xixi", 0);
		cc1.set("xixi", item);
		SerialItem item2 = (SerialItem) cc1.get("xixi");
		assertEquals(item, item2);
	}

	public void testSetExpire() throws InterruptedException {
		cc1.set("xixi", "0315", 1);
		assertEquals("0315", cc1.get("xixi"));

		Thread.sleep(2000);

		boolean res = cc1.keyExists("xixi");
		assertFalse(res);
	}

	public void testGetTouch() throws InterruptedException {
		long beginTime = CurrentTick.get();
		cc1.set("xixi", "session", 100);
		CacheItem item = cc1.gets("xixi");
		assertNotNull(item);
		long d = item.getExpireTime() - beginTime;
		assertEquals(d, 100);

		Thread.sleep(2000);

		item = cc1.gets("xixi");
		assertNotNull(item);
		d = item.getExpiration();
		assertEquals(d, 98);
		
		item = cc1.getAndTouch("xixi", 100);
		assertNotNull(item);
		d = item.getExpiration();
		assertEquals(d, 100);
		
		item = cc1.gets("xixi");
		assertNotNull(item);
		d = item.getExpiration();
		assertEquals(d, 100);
		assertNull(cc1.getLastError());
		
		item = cc1.getAndTouch("xixi", Defines.NO_EXPIRATION);
		assertNotNull(item);
		d = item.getExpiration();
		assertEquals(0, d);
		
		item = cc1.gets("xixi");
		assertNotNull(item);
		d = item.getExpiration();
		assertEquals(0, d);
		assertNull(cc1.getLastError());
	}

	public void testDelta() {
		assertNull(cc1.incr(null));
		long ret = cc1.add("xixi", "0");
		assertTrue(ret != 0);
		assertEquals(cc1.get("xixi"), Long.valueOf(0).toString());
		DeltaItem ditem = cc1.incr("xixi");
		ditem = cc1.incr("xixi", 5L);
		ditem = cc1.decr("xixi", 2L);
		assertEquals(ditem.value, 4);
		ditem = cc1.decr("xixi");
		assertEquals(ditem.value, 3);
		cc1.set("xixi", "9");
		ditem = cc1.incr("xixi", 0L);
		assertEquals(9, ditem.value);
	}

	public void testDelta2() throws InterruptedException {
		long ret = cc1.add("xixi", "0", 1);
		assertTrue(ret != 0);
		assertEquals(cc1.get("xixi"), Long.valueOf(0).toString());
		DeltaItem ditem = cc1.incr("xixi");
		ditem = cc1.incr("xixi", 5L);
		ditem = cc1.decr("xixi", 2L);
		assertEquals(4, ditem.value);
		
		Thread.sleep(2000);
		
		ditem = cc1.incr("xixi", 0L);
		assertNull(ditem);
		
		cc1.set("xixi", "9", 1);
		ditem = cc1.incr("xixi", 0L);
		assertEquals(9, ditem.value);
		
		Thread.sleep(2000);
		
		ditem = cc1.incr("xixi", 0L);
		assertNull(ditem);
	}

	public void testDelta3() throws InterruptedException {
		long ret = cc1.add("xixi", "0", 1);
		assertTrue(ret != 0);
		assertEquals(cc1.get("xixi"), Long.valueOf(0).toString());
		DeltaItem ditem = cc1.incr("xixi");
		ditem = cc1.incr("xixi", 5L);
		ditem = cc1.decr("xixi", 2L);
		assertEquals(ditem.value, 4L);
		assertTrue(ditem.cacheID != 0);
		
		assertNull(cc1.incr("xixi", 8, 123));
		ditem = cc1.incr("xixi", 8, ditem.cacheID);
		assertEquals(12, ditem.value);
		assertTrue(ditem.cacheID != 0);

		assertNull(cc1.decr("xixi", 7, 123));
		ditem = cc1.decr("xixi", 7, ditem.cacheID);
		assertEquals(5, ditem.value);
		assertTrue(ditem.cacheID != 0);
		
		ret = cc1.set("xixi", "9", 1, 123);
		assertEquals(0, ret);
		CacheItem item = cc1.gets("xixi");
		ret = cc1.set("xixi", "9", 1, item.getCacheID());
		assertTrue(ret != 0);
		
		ditem = cc1.incr("xixi", 0L);
		assertEquals(9, ditem.value);
		
		Thread.sleep(2000);
		
		ditem = cc1.incr("xixi", 0L);
		assertNull(ditem);
	}
	
	public void testAdd() {
		cc1.delete("xixi");
		assertEquals(null, cc1.get("xixi"));
		cc1.set("xixi", "0315");
		assertEquals("0315", cc1.get("xixi"));
		long ret = cc1.add("xixi", "20080315");
		assertEquals(0, ret);
		assertEquals("0315", cc1.get("xixi"));
		assertEquals(cc1.add(null, "ke,ai"), 0);
		assertEquals(cc1.add("xixi", null), 0);
	}

	public void testAddExpire() throws InterruptedException {
		cc1.add("xixi", "0315", 1);
		assertEquals("0315", cc1.get("xixi"));

		Thread.sleep(2000);

		assertNull(cc1.get("xixi"));
	}

	public void testAddExpire2() throws InterruptedException {
		cc1.add("xixi", "0315", 3);
		assertEquals("0315", cc1.get("xixi"));

		Thread.sleep(2000);

		assertEquals("0315", cc1.get("xixi"));
	}

	public void testReplaceStringObject() {
		cc1.set("xixi", "0315");
		cc1.replace("xixi", "20080315");
		assertEquals("20080315", cc1.get("xixi"));
	}

	public void testReplaceExpire() throws InterruptedException {
		cc1.set("xixi", "0315");
		cc1.replace("xixi", "20080315", 1);
		assertEquals("20080315", cc1.get("xixi"));

		Thread.sleep(2000);

		assertFalse(cc1.keyExists("xixi"));
	}

	public void testReplaceExpire2() throws InterruptedException {
		cc1.set("xixi", "0315");
		cc1.replace("xixi", "20080315", 3);
		assertEquals("20080315", cc1.get("xixi"));

		Thread.sleep(2000);

		assertEquals("20080315", cc1.get("xixi"));
	}

	public void testReplace3() {
		cc1.set("xixi", "0315", CacheClient.NO_EXPIRATION);
		cc1.replace("xixi", "20080315", CacheClient.NO_EXPIRATION);
		assertEquals("20080315", cc1.get("xixi"));
	}
	
	public void testReplace4() {
		cc1.set("xixi", "0315", CacheClient.NO_EXPIRATION);
		long ret = cc1.replace("xixi", "20080315", 3, 123);
		assertEquals(0, ret);
		assertEquals("0315", cc1.get("xixi"));
		CacheItem item = cc1.gets("xixi");
		ret = cc1.replace("xixi", "20080315", 3, item.getCacheID());
		assertTrue(ret != 0);
		assertEquals("20080315", cc1.get("xixi"));
	}

	public void testAppend() {
		cc1.append("xixi", "03");
		assertEquals(0, cc1.append(null, "15"));
		assertEquals(0, cc1.append("xixi", null));
		assertEquals(cc1.get("xixi"), null);
		cc1.add("xixi", "03");
		assertEquals(cc1.get("xixi"), "03");
		assertNotSame(cc1.append("xixi", "15"), 0);
		assertEquals(cc1.get("xixi"), "0315");
	}

	public void testAppend2() {
		long ret = cc1.add("xixi", "03");
		assertTrue(ret != 0);
		ret = cc1.append("xixi", "15", 99999);
		assertEquals(0, ret);
		CacheItem item = cc1.gets("xixi");
		ret = cc1.append("xixi", "15", item.getCacheID());
		assertTrue(ret != 0);
		assertEquals("0315", cc1.get("xixi"));
	}

	public void testPrepend() {
		long ret = cc1.prepend("xixi", "03");
		assertEquals(0, ret);
		assertEquals(0, cc1.prepend(null, "15"));
		assertEquals(0, cc1.prepend("xixi", null));
		assertEquals(cc1.get("xixi"), null);
		ret = cc1.add("xixi", "03");
		assertTrue(ret != 0);
		assertEquals(cc1.get("xixi"), "03");
		assertNotSame(cc1.prepend("xixi", "15"), 0);
		assertEquals(cc1.get("xixi"), "1503");
	}

	public void testPrepend2() {
		cc1.add("xixi", "03");
		long ret = cc1.prepend("xixi", "15", 123);
		assertEquals(0, ret);
		CacheItem item = cc1.gets("xixi");
		ret = cc1.prepend("xixi", "15", item.getCacheID());
		assertTrue(ret != 0);
		assertEquals("1503", cc1.get("xixi"));
	}

	public void testCacheIDExpire() {
		cc1.set("xixi", "0315");
		CacheItem item = cc1.gets("xixi");
		assertEquals("0315", item.getValue());
		
		cc1.set("xixi", "20080315", CacheClient.NO_EXPIRATION, item.getCacheID());
		item = cc1.gets("xixi");
		assertEquals("20080315", item.getValue());
		cc1.set("xixi", "0315");
		assertEquals("0315", cc1.get("xixi"));
		cc1.set("xixi", "ke,ai", CacheClient.NO_EXPIRATION, item.getCacheID());
		assertEquals("0315", cc1.get("xixi"));
	}

	public void testCacheIDExpire2() throws InterruptedException {
		cc1.set("xixi", "0315");
		CacheItem item = cc1.gets("xixi");
		assertEquals("0315", item.getValue());

		cc1.set("xixi", "20080315", 1, item.getCacheID());
		assertEquals("20080315", cc1.get("xixi"));

		Thread.sleep(2000);
		
		assertNull(cc1.get("xixi"));

		cc1.set("xixi", "ke,ai");
		assertEquals("ke,ai", cc1.get("xixi"));

		long ret = cc1.set("xixi", "?", 1, item.getCacheID());
		assertTrue(ret == 0);
	}

	public void testBigData() {
		int ct = cc1.getTransCoder().getCompressionThreshold();
		cc1.getTransCoder().setCompressionThreshold(0x7FFFFFFF);
		SerialItem item = new SerialItem("testBigData", 10240);
		for (int i = 0; i < 10; ++i) {
			cc1.set("xixi" + i, item);
			SerialItem item2 = (SerialItem)cc1.get("xixi" + i);
			assertEquals(item, item2);
		}
		item = new SerialItem("testBigData", 1024 * 1024 * 2);
		long ret = cc1.set("xixi", item);
		assertTrue(ret != 0);
		SerialItem item2 = (SerialItem)cc1.get("xixi");
		assertEquals(item, item2);
		cc1.getTransCoder().setCompressionThreshold(ct);
	}
	
	public void testBigData2() {
		SerialItem item = new SerialItem("testBigData", 10240);
		for (int i = 0; i < 10; ++i) {
			cc1.set("xixi" + i, item);
			SerialItem item2 = (SerialItem)cc1.get("xixi" + i);
			assertEquals(item, item2);
		}
		item = new SerialItem("testBigData2", 1024 * 1024 * 2);
		long ret = cc1.set("xixi", item);
		assertTrue(ret != 0);
		SerialItem item2 = (SerialItem)cc1.get("xixi");
		assertEquals(item, item2);
	}

	public static final class SerialItem implements Serializable {
		private static final long serialVersionUID = -1331512331067654578L;
		
		private String name;
		private byte[] buffer;
		private HashMap<String, Integer> map = new HashMap<String, Integer>();

		public SerialItem(String name, int size) {
			this.name = name;
			buffer = new byte[size];
			for (int i = 0; i < size; i++) {
				buffer[i] = (byte)(i & 0xFF);
			}
			for (int i = 0; i < 10; i++) {
				map.put(name + i, Integer.valueOf(i));
			}
		}

		public String getName() {
			return name;
		}

		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof SerialItem)) {
				return false;
			}
			SerialItem item = (SerialItem)obj;
			if (!name.equals(item.getName())) {
				return false;
			}
			if (buffer.length != item.buffer.length) {
				return false;
			}
			for (int i = 0; i < buffer.length; i++) {
				if (buffer[i] != item.buffer[i]) {
					return false;
				}
			}
			if (map.size() != item.map.size()) {
				return false;
			}
			for (int i = 0; i < 10; i++) {
				String key = name + i;
				Integer a = map.get(key);
				Integer b = item.map.get(key);
				if (a == null || b == null || !a.equals(b)) {
					return false;
				}
			}
			return true;
		}
		
		public int hashCode() {
			if (name == null) {
				return 0;
			}
			return name.hashCode(); 
		}
	}
	
	public void testStatsAddGroup() {
		boolean ret = cc1.statsAddGroup(null, 315);
		assertTrue(ret);
		ret = cc1.statsRemoveGroup(null, 315);
		assertTrue(ret);
	}
	
	public void testStatsAddGroup2() {
		for (int i = 0; i < 1024; i++) {
			boolean ret = cc1.statsAddGroup(null, i);
			assertTrue(ret);
		}
		boolean ret = cc1.statsAddGroup(null, 1024);
		assertFalse(ret);
		for (int i = 0; i < 1024; i++) {
			ret = cc1.statsRemoveGroup(null, i);
			assertTrue(ret);
		}
	}
	
	private void printStats(String name, Map<String, Map<String, String>> stats) {
		Iterator<Entry<String, Map<String, String>>> it = stats.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, Map<String, String>> e = it.next();
			String host = e.getKey();
			Map<String, String> m = e.getValue();
			Iterator<Entry<String, String>> it2 = m.entrySet().iterator();
			while (it2.hasNext()) {
				Entry<String, String> e2 = it2.next();
				String key = e2.getKey();
				String value = e2.getValue();
				System.out.println(name + " host=" + host + " " + key + "=" + value);
			}
		}
	}
	public void testStatsGet() {
		Map<String, Map<String, String>> stats = cc1.statsGetStats(null, (byte)0);
		assertNotNull(stats);
		printStats("testStatsGet", stats);
	}
	
	public void testStatsGet2() {
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				false, XixiWeightMap.CRC32_HASH, null, enableSSL);
		assertTrue(ccb.runIt());
		
		Map<String, Map<String, String>> stats = cc1.statsGetStats(null, (byte)0);
		assertNotNull(stats);
		printStats("testStatsGet2", stats);
	}
	
	public void testStatsGet3() {
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				true, XixiWeightMap.MD5_HASH, null, enableSSL);
		assertTrue(ccb.runIt());
		
		Map<String, Map<String, String>> stats = cc1.statsGetStats(null, (byte)11);
		assertNotNull(stats);
		printStats("testStatsGet3", stats);
	}
	
	public void testStatsGetError() {
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				true, XixiWeightMap.NATIVE_HASH, null, enableSSL);
		assertTrue(ccb.runIt());
		
		String[] serverlist = new String[1];
		serverlist[0] = "unkknownhost";
		Map<String, Map<String, String>> stats = cc1.statsGetStats(serverlist, (byte)11);
		assertNull(stats);
		String[] serverlist2 = new String[0];
		stats = cc1.statsGetStats(serverlist2, (byte)11);
		assertNull(stats);
	}
	
	public void testStatsGroupGet() {
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				true, XixiWeightMap.CRC32_HASH, null, enableSSL);
		assertTrue(ccb.runIt());
		
		Map<String, Map<String, String>> stats = cc1.statsGetGroupStats(null, 315, (byte)0);
		assertNotNull(stats);
		printStats("testStatsGroupGet", stats);
	}
	
	public void testStatsGroupGet2() {
		boolean ret = cc1.statsAddGroup(null, 315);
		assertTrue(ret);
		
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				false, XixiWeightMap.MD5_HASH, null, enableSSL);
		assertTrue(ccb.runIt());
		
		Map<String, Map<String, String>> stats = cc1.statsGetGroupStats(null, 315, (byte)1);
		assertNotNull(stats);
		printStats("testStatsGroupGet2", stats);
	}
	
	public void testStatsGroupGet3() {
		boolean ret = cc1.statsAddGroup(null, 315);
		assertTrue(ret);
		
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				false, 5, null, enableSSL);
		assertTrue(ccb.runIt());
		
		Map<String, Map<String, String>> stats = cc1.statsGetGroupStats(null, 315, (byte)0);
		assertNotNull(stats);
		
		ret = cc1.statsRemoveGroup(null, 315);
		assertTrue(ret);
		
		ccb = new CacheClientBench(servers, 1, 100, 315,
				false, XixiWeightMap.CRC32_HASH, null, enableSSL);
		assertTrue(ccb.runIt());
		
		stats = cc1.statsGetGroupStats(null, 315, (byte)0);
		assertNotNull(stats);
		printStats("testStatsGroupGet3", stats);
	}
	
	public void testStatsGroupError() {
		boolean ret = cc1.statsAddGroup(null, 315);
		assertTrue(ret);
		
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				true, XixiWeightMap.CRC32_HASH, null, enableSSL);
		assertTrue(ccb.runIt());
		
		String[] serverlist = new String[1];
		serverlist[0] = "unkknownhost";
		Map<String, Map<String, String>> stats = cc1.statsGetGroupStats(serverlist, 315, (byte)0);
		assertNull(stats);
	}
	
	public void testBench() {
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, enableSSL);
		assertTrue(ccb.runIt());
	}
	
	public void testBench2() {
		Integer[] weights = new Integer[5];
		weights[0] = Integer.valueOf(5);
		weights[1] = Integer.valueOf(110);
		weights[2] = Integer.valueOf(1);
		weights[3] = Integer.valueOf(0);
		weights[4] = Integer.valueOf(90);
		CacheClientBench ccb = new CacheClientBench(servers, 1, 100, 315,
				true, XixiWeightMap.CRC32_HASH, weights, enableSSL);
		assertTrue(ccb.runIt());
	}
	
	public void testStress() {
		StressTest st = new StressTest();
		int threadCount = 16;
		int keyCount = 1000;
		int setCount = 1000 * 3;
		int getCount = 1000 * 10;

		st.runIt(servers, enableSSL, threadCount, keyCount, setCount, getCount);
	}
	
	public void testUpdateFlags() {
		CacheClientManager mgr = CacheClientManager.getInstance("testUpdateFlags");
		mgr.setSocketWriteBufferSize(64 * 1024);
		mgr.initialize(serverlist, enableSSL);
		CacheClient cc = mgr.createClient(315);
		cc.flush();
		cc.set("xixi", "0315", 0, 0);
		
		String cn = cc.getTransCoder().getEncodingCharsetName();
		cc.getTransCoder().setEncodingCharsetName("encodingCharsetName");
		assertNull(cc.get("xixi"));
		cc.getTransCoder().setEncodingCharsetName(cn);
		
		CacheBaseItem item = cc.getBase("xixi");
		long cacheID = item.getCacheID();
		assertTrue(cacheID != 0);
		assertEquals(0, item.getOption1());
		assertEquals(0, item.getOption2());
		int flags = item.getFlags();
		flags = CacheBaseItem.setOption1(flags, (short)3);
		flags = CacheBaseItem.setOption2(flags, (byte)15);
		boolean ret = cc.updateFlags("xixi", flags, 0);
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertTrue(item.getCacheID() != 0);
		assertTrue(item.getCacheID() != cacheID);
		assertEquals(3, item.getOption1());
		assertEquals(15, item.getOption2());
		
		cacheID = cc.set("xixi", "", 0, 0);
		assertTrue(cacheID != 0);
		assertEquals("", cc.get("xixi"));
		
		item = cc.getBase("xixi");
		assertNotNull(item);
		flags = item.getFlags();
		ret = cc.updateFlags("xixi", 0, 123);
		assertFalse(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(flags, item.getFlags());

		ret = cc.updateFlags("xixi", 0, item.getCacheID());
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(0, item.getFlags());
		assertNull(cc.get("xixi"));
		
		/*
		 * 	public static final int FLAGS_TYPE_STRING = 10;
			public static final int FLAGS_TYPE_STRINGBUFFER = 11;
			public static final int FLAGS_TYPE_STRINGBUILDER = 12;
			public static final int FLAGS_TYPE_BYTEARR = 13;
		 */
		for (int i = 1; i <= 14; i++) {
			if (i >= 10 && i <= 13) {
				continue;
			}
			item = cc.getBase("xixi");
			ret = cc.updateFlags("xixi", i, item.getCacheID());
			assertTrue(ret);
			assertNull(cc.get("xixi"));
		}
		mgr.shutdown();
	}
	
	public void testUpdateFlagsError() {
		CacheClientManager mgr = CacheClientManager.getInstance("testUpdateFlagsError");
		mgr.setSocketWriteBufferSize(64 * 1024);
		mgr.initialize(serverlist, enableSSL);
		CacheClient cc = mgr.createClient(315);
		cc.flush();
		cc.set("xixi", "0315", 0, 0);
		
		boolean isSanitizeKeys = cc.getTransCoder().isSanitizeKeys();
		String cn = cc.getTransCoder().getEncodingCharsetName();
		cc.getTransCoder().setSanitizeKeys(true);
		cc.getTransCoder().setEncodingCharsetName("encodingCharsetName");
		assertNull(cc.get("xixi"));
		boolean ret = cc.updateFlags("xixi", 0, 0);
		assertFalse(ret);
		cc.getTransCoder().setSanitizeKeys(isSanitizeKeys);
		cc.getTransCoder().setEncodingCharsetName(cn);
		
		CacheBaseItem item = cc.getBase("xixi");
		long cacheID = item.getCacheID();
		assertTrue(cacheID != 0);
		assertEquals(0, item.getOption1());
		assertEquals(0, item.getOption2());
		int flags = item.getFlags();
		flags = CacheBaseItem.setOption1(flags, (short)3);
		flags = CacheBaseItem.setOption2(flags, (byte)15);
		ret = cc.updateFlags("xixi", flags, 0);
		assertTrue(ret);
		ret = cc.updateFlags("xixi2", flags, 0);
		assertFalse(ret);
		item = cc.getBase("xixi");
		assertTrue(item.getCacheID() != 0);
		assertTrue(item.getCacheID() != cacheID);
		assertEquals(3, item.getOption1());
		assertEquals(15, item.getOption2());
		
		cacheID = cc.set("xixi", "", 0, 0);
		assertTrue(cacheID != 0);
		assertEquals("", cc.get("xixi"));
		
		item = cc.getBase("xixi");
		assertNotNull(item);
		flags = item.getFlags();
		
		ret = cc.updateFlags(null, 0, item.getCacheID());
		assertFalse(ret);
		
		mgr.shutdown();
		ret = cc.updateFlags("xixi", 0, 0);
		assertFalse(ret);
	}
	
	public void testUpdateExpiration() {
		CacheClientManager mgr = CacheClientManager.getInstance("testUpdateExpiration");
		mgr.setSocketWriteBufferSize(64 * 1024);
		mgr.initialize(serverlist, enableSSL);
		CacheClient cc = mgr.createClient(315);
		cc.flush();
		cc.set("xixi", "0315", 20, 0);
		
		String cn = cc.getTransCoder().getEncodingCharsetName();
		cc.getTransCoder().setEncodingCharsetName("encodingCharsetName");
		assertNull(cc.get("xixi"));
		cc.getTransCoder().setEncodingCharsetName(cn);
		
		CacheBaseItem item = cc.getBase("xixi");
		long cacheID = item.getCacheID();
		assertTrue(cacheID != 0);
		assertEquals(20, item.getExpiration());
		long expiration = item.getExpiration();

		boolean ret = cc.updateExpiration("xixi", 2, 0);
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertTrue(item.getCacheID() != 0);
		assertTrue(item.getCacheID() == cacheID);
		assertEquals(2, item.getExpiration());
		
		item = cc.getBase("xixi");
		assertNotNull(item);
		expiration = item.getExpiration();
		ret = cc.updateExpiration("xixi", 5, 123);
		assertFalse(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(expiration, item.getExpiration());

		ret = cc.updateExpiration("xixi", 5, item.getCacheID());
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(5, item.getExpiration());
		assertNotNull(cc.get("xixi"));
		
		mgr.shutdown();
	}
	
	public void testUpdateExpirationError() {
		CacheClientManager mgr = CacheClientManager.getInstance("testUpdateExpirationError");
		mgr.setSocketWriteBufferSize(64 * 1024);
		mgr.initialize(serverlist, enableSSL);
		CacheClient cc = mgr.createClient(315);
		cc.flush();
		cc.set("xixi", "0315", 20, 0);
		
		boolean isSanitizeKeys = cc.getTransCoder().isSanitizeKeys();
		String cn = cc.getTransCoder().getEncodingCharsetName();
		cc.getTransCoder().setSanitizeKeys(true);
		cc.getTransCoder().setEncodingCharsetName("encodingCharsetName");
		assertNull(cc.get("xixi"));
		boolean ret = cc.updateExpiration("xixi", 0, 0);
		assertFalse(ret);
		cc.getTransCoder().setSanitizeKeys(isSanitizeKeys);
		cc.getTransCoder().setEncodingCharsetName(cn);

		CacheBaseItem item = cc.getBase("xixi");
		long cacheID = item.getCacheID();
		assertTrue(cacheID != 0);
		assertEquals(20, item.getExpiration());
		long expiration = item.getExpiration();

		ret = cc.updateExpiration("xixi", 2, 0);
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertTrue(item.getCacheID() != 0);
		assertTrue(item.getCacheID() == cacheID);
		assertEquals(2, item.getExpiration());
		
		item = cc.getBase("xixi");
		assertNotNull(item);
		expiration = item.getExpiration();
		ret = cc.updateExpiration("xixi", 5, 123);
		assertFalse(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(expiration, item.getExpiration());

		ret = cc.updateExpiration("xixi", 5, item.getCacheID());
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(5, item.getExpiration());
		assertNotNull(cc.get("xixi"));
		
		ret = cc.updateExpiration(null, 5, item.getCacheID());
		assertFalse(ret);
		
		mgr.shutdown();
		ret = cc.updateExpiration("xixi", 0, 0);
		assertFalse(ret);
	}
	
	public void testOtherError() {
		CacheClientManager mgr = CacheClientManager.getInstance("testOtherError");
		mgr.setSocketWriteBufferSize(64 * 1024);
		mgr.initialize(serverlist, enableSSL);
		CacheClient cc = mgr.createClient(315);
		cc.flush();
		cc.set("xixi", "0315", 20, 0);
		
		boolean isSanitizeKeys = cc.getTransCoder().isSanitizeKeys();
		String cn = cc.getTransCoder().getEncodingCharsetName();
		cc.getTransCoder().setSanitizeKeys(true);
		cc.getTransCoder().setEncodingCharsetName("encodingCharsetName");
		assertNull(cc.get("xixi"));
		assertFalse(cc.delete("xixi", 0));
		assertEquals(0, cc.set("xixi", "0315"));
		assertNull(cc.incr("xixi"));
		boolean ret = cc.updateExpiration("xixi", 0, 0);
		assertFalse(ret);
		cc.getTransCoder().setSanitizeKeys(isSanitizeKeys);
		cc.getTransCoder().setEncodingCharsetName(cn);

		CacheBaseItem item = cc.getBase("xixi");
		long cacheID = item.getCacheID();
		assertTrue(cacheID != 0);
		assertEquals(20, item.getExpiration());
		long expiration = item.getExpiration();

		ret = cc.updateExpiration("xixi", 2, 0);
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertTrue(item.getCacheID() != 0);
		assertTrue(item.getCacheID() == cacheID);
		assertEquals(2, item.getExpiration());
		
		item = cc.getBase("xixi");
		assertNotNull(item);
		expiration = item.getExpiration();
		ret = cc.updateExpiration("xixi", 5, 123);
		assertFalse(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(expiration, item.getExpiration());

		assertEquals(expiration, item.getExpiration());
		ret = cc.updateExpiration("xixi", 5, item.getCacheID());
		assertTrue(ret);
		item = cc.getBase("xixi");
		assertNotNull(item);
		assertEquals(5, item.getExpiration());
		assertNotNull(cc.get("xixi"));
		
		ret = cc.updateExpiration(null, 5, item.getCacheID());
		assertFalse(ret);
		
		CacheClientImpl cc2 = new CacheClientImpl(mgr, 315);
		
		int watchID = cc2.createWatch(mgr.getServers()[0], 100);
		assertTrue(watchID != 0);
		assertNotNull(cc2.checkWatch(mgr.getServers()[0], watchID, 1, 100, 0));
		assertNull(cc2.checkWatch(mgr.getServers()[0], 99999, 1, 100, 0));
		
		mgr.shutdown();
		ret = cc.updateExpiration("xixi", 0, 0);
		assertFalse(ret);
		assertEquals(0, cc.set("xixi", "0315"));
		
		assertFalse(cc.delete("xixi", 0));
		
		assertNull(cc.incr("xixi"));
		
		
		assertEquals(0, cc2.createWatch(mgr.getServers()[0], 100));
		assertNull(cc2.checkWatch(mgr.getServers()[0], 1, 1, 100, 0));
	}
}
