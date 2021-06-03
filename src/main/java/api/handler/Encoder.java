package api.handler;

import api.Application;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class Encoder {

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static String encode(VideoData data) throws IOException {
		int w = alignData(data.getWidth());
		int h = alignData(data.getHeight());

		File f = new File(Application.files, data.getHash() + ".mp4");
		if (!f.exists())
			f.createNewFile();

		FFmpegFrameRecorder rec = new FFmpegFrameRecorder(f, w, h, 0);
		rec.setVideoCodec(avcodec.AV_CODEC_ID_H264);
		rec.setVideoOption("preset", "veryfast");
		rec.setOption("hwaccel", "opencl");
		rec.setVideoBitrate(2 * 1024 * 1024);
		rec.setVideoQuality(0);
		rec.setFrameRate(1);
		rec.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
		rec.setFormat("mp4");
		rec.start();

		List<BufferedImage> frames = data.getDecodedFrames();
		Java2DFrameConverter conv = new Java2DFrameConverter();
		for (BufferedImage frame : frames) {
			rec.record(conv.convert(frame), avutil.AV_PIX_FMT_0RGB);
		}

		rec.stop();

		return data.getHash();
	}

	private static BufferedImage center(BufferedImage in, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, in.getType());
		Graphics2D g2d = out.createGraphics();
		g2d.drawImage(in, (out.getWidth() - in.getWidth()) / 2, (out.getHeight() - in.getHeight()) / 2, null);
		g2d.dispose();

		return out;
	}

	private static int alignData(int n) {
		return n + 32 - (n % 32);
	}
}
