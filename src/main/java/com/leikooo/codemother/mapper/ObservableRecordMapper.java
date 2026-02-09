package com.leikooo.codemother.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leikooo.codemother.model.entity.ObservableRecord;
import com.leikooo.codemother.model.vo.AppRankingVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author leikooo
 * @description 针对表【observable_record】的数据库操作Mapper
 * @createDate 2026-01-15
 * @Entity com.leikooo.codemother.model.entity.ObservableRecord
 */
@Mapper
public interface ObservableRecordMapper extends BaseMapper<ObservableRecord> {

    ObservableRecord getAppStatistics(@Param("appId") String appId);

    Long countChatsByAppId(@Param("appId") String appId);

    List<AppRankingVO> getRanking(@Param("type") String type, @Param("limit") Integer limit);
}
