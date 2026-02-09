package com.leikooo.codemother.model.vo;

import com.leikooo.codemother.model.entity.App;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author leikooo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppRankingVO implements Serializable {
    private Long appId;
    private String appName;
    private Long value;
    private App app;
}
