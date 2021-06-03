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
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class EncoderSocket extends WebSocketServer {
	private final Map<String, VideoData> pending = new HashMap<>();
	private final EncodingQueue queue = new EncodingQueue();
	private WebSocket client = null;

	public EncoderSocket(InetSocketAddress address) {
		super(address);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		if (client != null) {
			conn.send(new JSONObject() {{
				put("code", HttpStatus.LOCKED.value());
				put("message", "Another client is already connected to socket");
			}}.toString());
			conn.close();
			return;
		}
		client = conn;

		Application.logger.info("Connection estabilished: " + conn.getLocalSocketAddress().toString());
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		client = null;
		Application.logger.info("Connection undone");
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
					Application.logger.info("Ping received");
				}
				case BEGIN -> {
					if (pending.containsKey(hash)) {
						conn.send(new JSONObject() {{
							put("code", HttpStatus.METHOD_NOT_ALLOWED.value());
							put("message", "Packet stream already opened for hash " + hash);
						}}.toString());
						return;
					}
					int size = data.getInt("size");
					pending.put(hash, new VideoData(hash, size, data.getInt("width"), data.getInt("height")));
					Application.logger.info("Received payload (total frames: " + size + ") with hash " + hash + ": Data stream BEGIN");
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
					vd.getFrames().add(data.getString("data"));
					Application.logger.info("Received payload (" + vd.getFrames().size() + "/" + vd.getSize() + ") with hash " + hash + ": Data stream NEXT");
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
					queue.queue(vd);
					Application.logger.info("Received payload (" + (vd.getFrames().size() * 100 / vd.getSize()) + "% received) with hash " + hash + ": Data stream END");
					jo.put("code", HttpStatus.PROCESSING.value());
				}
			}

			conn.send(jo.toString());
		} catch (JSONException e) {
			conn.send(new JSONObject() {{
				put("code", HttpStatus.BAD_REQUEST.value());
				put("message", "Not enough fields were supplied for this type");
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

	public WebSocket getClient() {
		return client;
	}
}