package com.neha.StreamHub;

import com.neha.StreamHub.services.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StreamHubApplicationTests {

	@Autowired
	VideoService videoService;

	@Test
	void contextLoads() {
	}

}
