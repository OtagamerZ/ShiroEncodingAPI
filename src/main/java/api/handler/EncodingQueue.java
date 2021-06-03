package api.handler;

import api.Application;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;

import java.io.IOException;

public class EncodingQueue {
	public void queue(VideoData data) {
		if (!data.isComplete()) {
			Application.sockets.getEncoder().getClient().send(new JSONObject(){{
					put("code", HttpStatus.EXPECTATION_FAILED.value());
					put("message", "Invalid frame size: received " + data.getFrames().size() + " | expected " + data.getSize());
			}}.toString());
			return;
		}

		Application.getExec().execute(() -> Application.sockets.getEncoder().getClient().send(new JSONObject(){{
			put("code", HttpStatus.OK.value());
			put("hash", data.getHash());
			try {
				put("url", "https://api.shirojbot.site/replay?p=" + Encoder.encode(data));
			} catch (IOException e) {
				put("url", "");
			}
		}}.toString()));
	}
}
