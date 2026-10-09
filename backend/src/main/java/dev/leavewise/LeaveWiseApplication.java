package dev.leavewise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. Serves the API and the built React page from one process. */
@SpringBootApplication
public class LeaveWiseApplication {

  public static void main(String[] args) {
    SpringApplication.run(LeaveWiseApplication.class, args);
  }
}
