package org.demo.controller;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: xdx
 * @date: 2024/5/16
 * @description: 打包下载网络文件，多线程读，单线程写
 * Zip 输出流如果想添加新的 ZipEntry，必须关闭上一个 ZipEntry，所以在多线程情况下要加锁，保证只有一个线程进行写操作
 */
@Slf4j
@RequestMapping("/zip")
@RestController
@RequiredArgsConstructor
public class ZipDownloadController {

	public static final ExecutorService executorService = Executors.newFixedThreadPool(10);
	public static final String API = "https://demo.com/api"; //替换成你的
	public static final String REFERER = "https://demo.com/";//替换成你的
	/**
	 * 多线程读，单线程写
	 */
	@GetMapping()
	public void downloadZip(HttpServletResponse response) throws IOException, InterruptedException {

		// 设置文件类型和名称
		String zipFileName = "demo.zip";
		response.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
		response.setContentType(ContentType.OCTET_STREAM.getValue());

		// 网络文件的URL
		List<String> filesURL = getFilesURL();

		// 计数器，多线程操作，需要等待所有线程都完成，才能关闭 Zip 输出流
		CountDownLatch countDownLatch = new CountDownLatch(filesURL.size());

		// 加锁，Zip 输出流如果想添加新的 ZipEntry，必须关闭上一个 ZipEntry，所以在多线程情况下要加锁
		Lock lock = new ReentrantLock();

		// response 输出流
		try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(response.getOutputStream())) {
			for (String fileURL : filesURL) {
				executorService.execute(() -> {
					HttpURLConnection connection = getHttpURLConnection(fileURL);
					if (connection == null) {
						return;
					}
					lock.lock();// 加锁
					String fileName = FileNameUtil.getName(fileURL.split("\\?")[0]);
					writeZip(connection, zipOut, fileName);
					countDownLatch.countDown();// 每完成一个，计数器减一,否则主线程会一直等待
					lock.unlock();// 释放锁
				});
			}
			// 阻塞，等待全部线程操作完成
			countDownLatch.await();
		}
	}


	private static void writeZip(HttpURLConnection connection, ZipArchiveOutputStream zipOut,
		String fileName) {
		log.info("开始下载文件：{}",fileName);
		try (BufferedInputStream inputStream = new BufferedInputStream(
			connection.getInputStream())) {
			zipOut.putArchiveEntry(new ZipArchiveEntry(fileName));
			byte[] bytes = new byte[4096];
			int length;
			while ((length = inputStream.read(bytes)) >= 0) {
				zipOut.write(bytes, 0, length);
			}
			zipOut.closeArchiveEntry();
			zipOut.flush();
		} catch (IOException e) {
			log.info("文件下载异常：{}", fileName, e);
		} finally {
			connection.disconnect();
		}
	}

	/**
	 * 获取 HttpURLConnection
	 */
	private static HttpURLConnection getHttpURLConnection(String fileUrl) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
			connection.setRequestMethod(RequestMethod.GET.name());
			connection.setRequestProperty(Header.REFERER.getValue(), REFERER);
			return connection;
		} catch (IOException e) {
			log.info("HttpURLConnection 异常", e);
		}
		return null;
	}


	/**
	 * 获取文件的URL
	 */
	private static List<String> getFilesURL() {
		List<String> filesURL = new ArrayList<>();
		String body = HttpRequest.get(API)
			.header(Header.REFERER.getValue(), REFERER)
			.contentType(ContentType.JSON.getValue())
			.timeout(10000)//超时，毫秒
			.execute().body();
		JSONObject jsonObject = JSON.parseObject(body);
		JSONArray jsonArray = jsonObject.getJSONArray("data");
		jsonArray.forEach(item -> filesURL.add(item.toString()));
		return filesURL;
	}

}
