package api.handler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class VideoData {
	private final String hash;
	private final int size;
	private final int width;
	private final int height;
	private final List<String> frames = new ArrayList<>();
	private long last;

	public VideoData(String hash, int size, int width, int height) {
		this.hash = hash;
		this.size = size;
		this.width = width;
		this.height = height;
		this.last = System.currentTimeMillis();
	}

	public String getHash() {
		return hash;
	}

	public int getSize() {
		return size;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean isComplete() {
		return size == frames.size();
	}

	public List<String> getFrames() {
		return frames;
	}

	public List<BufferedImage> getDecodedFrames() {
		Base64.Decoder d = Base64.getDecoder();

		return frames.stream()
				.map(f -> {
					try (ByteArrayInputStream bais = new ByteArrayInputStream(d.decode(f))) {
						return ImageIO.read(bais);
					} catch (IOException | NullPointerException e) {
						return null;
					}
				})
				.collect(Collectors.toList());
	}

	public long getLast() {
		return last;
	}

	public void setLast() {
		this.last = System.currentTimeMillis();
	}
}
