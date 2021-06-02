package api.handler;

import api.Application;
import org.json.JSONObject;

import java.io.IOException;

public class EncodingQueue {
	public void queue(VideoData data) {
		Application.getExec().submit(() -> Application.sockets.getEncoder().getClient().send(new JSONObject(){{
			try {
				put("url", "https://api.shirojbot.site/replay?p=" + Encoder.encode(data));
			} catch (IOException e) {
				put("url", "");
			}
		}}.toString()));
	}
}
