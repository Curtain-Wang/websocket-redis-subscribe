package com.curtain.sub;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "web-socket")
@Data
public class WebsocketProperties {

    private Integer period; //频率(s)

}
