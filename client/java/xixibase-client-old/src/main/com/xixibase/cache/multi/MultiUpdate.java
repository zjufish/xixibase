/*
   Copyright [2011] [Yao Yuan(yeaya@163.com)]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.xixibase.cache.multi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import com.xixibase.cache.AsyncHandle;
import com.xixibase.cache.CacheClientManager;
import com.xixibase.cache.Defines;
import com.xixibase.cache.LocalCache;
import com.xixibase.cache.TransCoder;
import com.xixibase.cache.XixiSocket;
import com.xixibase.util.Log;

public final class MultiUpdate extends Defines {
	private static Log log = Log.getLog(MultiUpdate.class.getName());

	private CacheClientManager manager;
	private int groupID;
	private TransCoder transCoder;
	
	private Selector selector;
	private int numConns = 0;
	private byte opFlag = 0;
	private AtomicInteger successCount = new AtomicInteger(0);
	private String lastError = null;
	private LocalCache localCache = null;
	
	public MultiUpdate(CacheClientManager manager, int groupID, TransCoder transCoder) {
		this.manager = manager;
		this.groupID = groupID;
		this.transCoder = transCoder;
		this.localCache = manager.getLocalCache();
	}
	
	public String getLastError() {
		return lastError;
	}

	public int multiUpdate(List<MultiUpdateItem> list, byte subOp) {
		lastError = null;
		opFlag = (byte)(subOp | XIXI_UPDATE_REPLY);
		if (list == null) {
			lastError = "multiUpdate, list == null";
			log.error(lastError);
			return 0;
		}
		if (list.size() == 0) {
			lastError = "multiUpdate, list.size() == 0";
			log.error(lastError);
			return 0;
		}
		Map<String, Connection> conns = new HashMap<String, Connection>();
		try {
			Iterator<MultiUpdateItem> it = list.iterator();
			int index = 0;
			while (it.hasNext()) {
				Integer keyIndex = Integer.valueOf(index);
				index++;
				MultiUpdateItem item = it.next();
				if (item == null) {
					lastError = "multiUpdate, item == null";
					log.error(lastError);
					continue;
				}

				if (item.key == null) {
					lastError = "multiUpdate, item.key == null";
					log.error(lastError);
					continue;
				}
				
				byte[] keyBuf = transCoder.encodeKey(item.key);
				if (keyBuf == null) {
					lastError = "multiUpdate, failed to encode key";
					log.error(lastError);
					continue;
				}
				
				if (item.value == null) {
					lastError = "multiUpdate, item.value == null";
					log.error(lastError);
					continue;
				}

				String host = manager.getHost(item.key);
				if (host == null) {
					lastError = "multiUpdate, can not get host with the key";
					log.error(lastError);
					continue;
				}

				Connection conn = conns.get(host);
				if (conn == null) {
					conn = new Connection();
					conns.put(host, conn);
				}
				conn.add(item, keyBuf, keyIndex);
			}
			
			selector = manager.selectorOpen();

			Iterator<Entry<String, Connection>> itc = conns.entrySet().iterator();
			while (itc.hasNext()) {
				Entry<String, Connection> e = itc.next();
				String host = e.getKey();
				Connection conn = e.getValue();
				XixiSocket socket = manager.getSocketByHost(host);

				if (socket != null) {
					conn.init(socket);
				}
			}

			numConns = conns.size();

			long startTime = System.currentTimeMillis();
			long timeout = manager.getMaxBusyTime();
			long timeRemaining = timeout;

			while (numConns > 0 && timeRemaining > 0) {
				int n = selector.select(timeRemaining);
				if (n > 0) {
					Iterator<SelectionKey> its = selector.selectedKeys().iterator();
					while (its.hasNext()) {
						SelectionKey key = its.next();
						its.remove();
						handleKey(key);
					}
				} else {
					lastError = "multiUpdate, selector timed out";
					log.error(lastError);
					break;
				}

				timeRemaining = timeout - (System.currentTimeMillis() - startTime);
			}
		} catch (IOException e) {
			lastError = "multiUpdate, exception" + e;
			log.error(lastError);
			e.printStackTrace();
		} finally {
			try {
				manager.selectorClose(selector);
			} catch (IOException e) {
				lastError = "multiUpdate, close selector exception :" + e;
				log.error(lastError);
				e.printStackTrace();
			}
			Iterator<Connection> itc = conns.values().iterator();
			while (itc.hasNext()) {
				Connection conn = itc.next();
				conn.close();
			}
		}

		return successCount.intValue();
	}

	private void handleKey(SelectionKey key) throws IOException {
		if (key.isValid()) {
			if (key.isReadable()) {
				readResponse(key);
			} else if (key.isWritable()) {
				writeRequest(key);
			}
		}
	}

	private void writeRequest(SelectionKey key) throws IOException {
		XixiSocket socket = (XixiSocket) key.attachment();
		if (socket.handleWrite()) {
			key.cancel();
			numConns--;
		}
	}

	private void readResponse(SelectionKey key) throws IOException {
		XixiSocket socket = (XixiSocket) key.attachment();
		if (socket.handleRead()) {
			key.cancel();
			numConns--;
		}
	}
	
	private final class Connection implements AsyncHandle {
		private ByteBuffer outBuffer;
		private XixiSocket socket;
		private boolean isDone = false;
		private ArrayList<MultiUpdateItem> items = new ArrayList<MultiUpdateItem>();
		private ArrayList<byte[]> keyBuffers = new ArrayList<byte[]>();
		private ArrayList<Integer> itemIndexs = new ArrayList<Integer>();
		private int currKeyIndex = 0;

		public void add(MultiUpdateItem item, byte[] keyBuffer, Integer index) {
			items.add(item);
			keyBuffers.add(keyBuffer);
			itemIndexs.add(index);
		}

		MultiUpdateItem item = null;
		byte[] keyBuf = null;
		byte[] data = null;
		int dataSize = 0;
		int flags = 0;
		private void encode() throws IOException {
			if (dataSize == 0) {
				item = items.get(currKeyIndex);
				keyBuf = keyBuffers.get(currKeyIndex);

				int[] outflags = new int[1];
				data = transCoder.encode(item.value, outflags, null);
				flags = outflags[0];
				dataSize = data.length;
			}
			int totalLen = 33 + keyBuf.length + dataSize;
			if (outBuffer.limit() < totalLen) {
				outBuffer = ByteBuffer.allocateDirect(totalLen);
			}
			outBuffer.put(XIXI_CATEGORY_CACHE);
			outBuffer.put(XIXI_TYPE_UPDATE_REQ);
			outBuffer.put(opFlag);
			outBuffer.putLong(item.cacheID);//uint64_t cacheID;
			outBuffer.putInt(groupID);
			outBuffer.putInt(flags); // flags
			outBuffer.putInt(item.expiration);//			uint32_t expiration;
			outBuffer.putInt(NO_WATCH); // watchID
			outBuffer.putShort((short) keyBuf.length); // uint16_t key_length;

			outBuffer.putInt(dataSize); // uint32_t data_length;
			outBuffer.put(keyBuf);

			outBuffer.put(data);
			currKeyIndex++;
			
			while (currKeyIndex < items.size()) {
				item = items.get(currKeyIndex);
				keyBuf = keyBuffers.get(currKeyIndex);

				int[] outflags = new int[1];
				data = transCoder.encode(item.value, outflags, null);
				flags = outflags[0];
				dataSize = data.length;

				totalLen = 33 + keyBuf.length + dataSize;
				if (outBuffer.limit() - outBuffer.position() < totalLen) {
					break;
				}
				outBuffer.put(XIXI_CATEGORY_CACHE);
				outBuffer.put(XIXI_TYPE_UPDATE_REQ);
				outBuffer.put(opFlag);
				outBuffer.putLong(item.cacheID);//uint64_t cacheID;
				outBuffer.putInt(groupID);
				outBuffer.putInt(flags); // flags
				outBuffer.putInt(item.expiration);//			uint32_t expiration;
				outBuffer.putInt(NO_WATCH); // watchID
				outBuffer.putShort((short) keyBuf.length); // uint16_t key_length;

				outBuffer.putInt(dataSize); // uint32_t data_length;
				outBuffer.put(keyBuf);

				outBuffer.put(data);
				currKeyIndex++;
			}
		}
		
		public void init(XixiSocket socket) throws IOException {
			this.socket = socket;
			outBuffer = ByteBuffer.allocateDirect(64 * 1024);
			
			encode();
			
			outBuffer.flip();
		//	byteChannel = socket.getByteChannel();
		//	selectableChannel = socket.getSelectableChannel();
	//		channel = socket.getChannel();
			socket.configureBlocking(false);
			socket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
		}

		public int processRequest() throws IOException {
			int count = socket.write(outBuffer);
			if (count > 0) {
				return count;
			}
			if (currKeyIndex >= items.size()) {
				socket.register(selector, SelectionKey.OP_READ, this);
				return 0;
			}
			outBuffer.flip();
			
			encode();

			outBuffer.flip();
			
			count = socket.write(outBuffer);
			
			return count;
		}

		public void close() {
			if (socket != null) {
				try {
					if (isDone) {
						socket.configureBlocking(true);
						socket.close();
						socket = null;
						return;
					}
				} catch (IOException e) {
					lastError = "close, " + e;
					log.warn(lastError);
				}
	
				socket.trueClose();
				socket = null;
			}
		}

		private static final int STATE_READ_HEAD = 0;
		private static final int STATE_READ_FIXED_BODY = 1;
		private static final int STATE_READ_ERROR = 2;
		private int state = STATE_READ_HEAD;
		private static final int HEADER_LENGTH = 2;
		private ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH);
		private static final int FIXED_LENGTH = 8;
		private ByteBuffer fixed = ByteBuffer.allocate(FIXED_LENGTH);
		private static final int ERROR_RES_LENGTH = 2;
		private ByteBuffer error_res = ByteBuffer.allocate(ERROR_RES_LENGTH);
		private long cacheID = 0;
		private int decode_count = 0;
		public boolean processResponse() throws IOException {
			boolean run = true;
			while(run) {
				if (state == STATE_READ_HEAD) {
					int ret = socket.read(header);
					if (ret <= 0) {
						run = false;
					} else if (header.position() == HEADER_LENGTH) {
						header.flip();
						byte category = header.get();
						byte type = header.get();
						if (category == XIXI_CATEGORY_CACHE && type == XIXI_TYPE_UPDATE_RES) {
							state = STATE_READ_FIXED_BODY;
							fixed = ByteBuffer.allocate(FIXED_LENGTH);
						} else {
							state = STATE_READ_ERROR;
							error_res = ByteBuffer.allocate(ERROR_RES_LENGTH);
						}
					}
				}
				if (state == STATE_READ_FIXED_BODY) {
					int ret = socket.read(fixed);
					if (ret <= 0) {
						run = false;
					} else if (fixed.position() == FIXED_LENGTH) {
						fixed.flip();
						cacheID = fixed.getLong();
						MultiUpdateItem updateItem = items.get(decode_count);
						localCache.remove(socket.getHost(), groupID, updateItem.key);
						updateItem.newCacheID = cacheID;
						decode_count++;
						successCount.incrementAndGet();
						
						if (items.size() == decode_count) {
							isDone = true;
							run = false;
						} else {
							header = ByteBuffer.allocate(HEADER_LENGTH);
							state = STATE_READ_HEAD;
						}
					}
				}
				if (state == STATE_READ_ERROR) {
					int ret = socket.read(error_res);
					if (ret <= 0) {
						run = false;
					} else if (error_res.position() == ERROR_RES_LENGTH) {
						error_res.flip();
						short reason = error_res.getShort();

						items.get(decode_count).reason = reason;
						decode_count++;

						lastError = "processResponse, response error reason=" + reason;
						log.warn(lastError);
						if (items.size() == decode_count) {
							isDone = true;
							run = false;
						} else {
							header = ByteBuffer.allocate(HEADER_LENGTH);
							state = STATE_READ_HEAD;
						}
					}
				}
			}
			return isDone;
		}
		
		@Override
		public boolean onRead() throws IOException  {
			this.processResponse();
			return isDone;
		}

		@Override
		public boolean onWrite() throws IOException  {
			this.processRequest();
			return isDone;
			
		}
	}
}
