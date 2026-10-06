package com.naengjang_goat.inventory_system.workflow;

import java.time.*;
import org.springframework.context.annotation.*;

@Configuration
public class WorkflowTime {
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  @Bean
  public Clock workflowClock() {
    return Clock.system(ZONE);
  }
}
