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

package api;

import api.handler.WebSocketConfig;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

@SpringBootApplication
public class Application {
	public static final File files = new File("files");
	public static final Logger logger = LoggerFactory.getLogger(Application.class);
	public static final WebSocketConfig sockets = new WebSocketConfig();
	private static final ExecutorService exec = Executors.newSingleThreadExecutor();

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void main(String[] args) {
		files.mkdir();
		SpringApplication.run(Application.class, args);
	}

	public static ExecutorService getExec() {
		return exec;
	}

	public static String uncompress(byte[] compressed) throws IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
		GZIPInputStream gis = new GZIPInputStream(bis);
		byte[] bytes = IOUtils.toByteArray(gis);
		return new String(bytes, StandardCharsets.UTF_8);
	}
}