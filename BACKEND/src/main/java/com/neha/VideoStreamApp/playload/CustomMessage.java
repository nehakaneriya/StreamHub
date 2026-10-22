package com.neha.VideoStreamApp.playload;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CustomMessage {

    private String message;

    @Builder.Default
    private boolean success=false;
}
