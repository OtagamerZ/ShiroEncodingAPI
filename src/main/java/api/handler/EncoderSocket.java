/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2020  Yago Gimenez (KuuHaKu)
 *
 * Shiro J Bot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shiro J Bot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shiro J Bot.  If not, see <https://www.gnu.org/licenses/>
 */

package api.handler;

import api.Application;
import org.apache.commons.lang3.ArrayUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EncoderSocket extends WebSocketServer {
	private final Map<String, WebSocket> clients = new HashMap<>();
	private final Map<String, VideoData> pending = new HashMap<>();
	private final EncodingQueue queue = new EncodingQueue();

	public EncoderSocket(InetSocketAddress address) {
		super(address);

		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
			Iterator<Map.Entry<String, VideoData>> iterator = pending.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, VideoData> entry = iterator.next();

				if (System.currentTimeMillis() - entry.getValue().getLast() > 60000) {
					iterator.remove();
					clients.remove(entry.getKey()).send(new JSONObject() {{
						put("code", HttpStatus.REQUEST_TIMEOUT.value());
						put("message", "Time between packets cannot exceed 1 minute");
					}}.toString());
					Application.logger.info("Request timeouted with hash " + entry.getKey() + ": Data stream TIMEOUT");
				}
			}
		}, 0, 2000, TimeUnit.MILLISECONDS);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		/*if (!handshake.getFieldValue("Authentication").equals(System.getenv("AUTH"))) {
			Application.logger.info("Denied connection: " + conn.getRemoteSocketAddress().toString());
			conn.send(new JSONObject() {{
				put("code", HttpStatus.UNAUTHORIZED.value());
				put("message", "Connection not authorized for supplied token");
			}}.toString());
			conn.close(CloseFrame.REFUSE);
			return;
		}*/

		Application.logger.debug("Connection estabilished: " + conn.getRemoteSocketAddress().toString());
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		Application.logger.debug("Connection undone");
	}

	@Override
	public void onMessage(WebSocket conn, String payload) {
		try {
			JSONObject data = new JSONObject(payload);

			String hash = data.getString("hash");
			DataType type = data.getEnum(DataType.class, "type");
			JSONObject jo = new JSONObject() {{
				put("hash", hash);
				put("type", type);
			}};

			switch (type) {
				case PING -> {
					jo.put("code", HttpStatus.FOUND.value());
					Application.logger.info("Ping received from " + conn.getRemoteSocketAddress().toString());
				}
				case BEGIN -> {
					clients.put(hash, conn);
					if (pending.containsKey(hash)) {
						conn.send(new JSONObject() {{
							put("code", HttpStatus.METHOD_NOT_ALLOWED.value());
							put("message", "Packet stream already opened for hash " + hash);
						}}.toString());
						return;
					}
					int size = data.getInt("size");
					pending.put(hash, new VideoData(hash, size, data.getInt("width"), data.getInt("height")));
					Application.logger.info("Received request header (total frames: " + size + ") with hash " + hash + ": Data stream BEGIN");
					jo.put("code", HttpStatus.CREATED.value());
				}
				case NEXT -> {
					VideoData vd = pending.getOrDefault(hash, null);
					if (vd == null) {
						conn.send(new JSONObject() {{
							put("code", HttpStatus.METHOD_NOT_ALLOWED.value());
							put("message", "Packet stream not opened yet for hash " + hash);
						}}.toString());
						return;
					}
					vd.setLast();
					Byte[] bytes = data.getJSONArray("data").toList().stream()
							.map(o -> (byte) (int) o)
							.toArray(Byte[]::new);
					vd.getFrames().add(Application.uncompress(ArrayUtils.toPrimitive(bytes)));
					Application.logger.info("Received request packet (" + vd.getFrames().size() + "/" + vd.getSize() + ") with hash " + hash + ": Data stream NEXT");
					jo.put("code", HttpStatus.CONTINUE.value());
				}
				case END -> {
					VideoData vd = pending.remove(hash);
					if (vd == null) {
						conn.send(new JSONObject() {{
							put("code", HttpStatus.METHOD_NOT_ALLOWED.value());
							put("message", "Packet stream not open for hash " + hash);
						}}.toString());
						return;
					}
					vd.setLast();
					queue.queue(vd);
					Application.logger.info("Received request trailer (" + (vd.getFrames().size() * 100 / vd.getSize()) + "% received) with hash " + hash + ": Data stream END");
					jo.put("code", HttpStatus.PROCESSING.value());
				}
			}

			conn.send(jo.toString());
		} catch (JSONException e) {
			conn.send(new JSONObject() {{
				put("code", HttpStatus.BAD_REQUEST.value());
				put("message", "Not enough fields were supplied for this type");
			}}.toString());
		} catch (IOException e) {
			conn.send(new JSONObject() {{
				put("code", HttpStatus.BAD_REQUEST.value());
				put("message", "Wrong or uncompressable data type sent as packet");
			}}.toString());
		}
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {

	}

	@Override
	public void onStart() {
		Application.logger.info("WebSocket \"encoder\" iniciado na porta " + this.getPort());
	}

	public WebSocket getClient(String hash) {
		return clients.remove(hash);
	}
}