package com.leikooo.codemother.mapper;

import com.leikooo.codemother.model.entity.App;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
* @author leikooo
* @description 针对表【app(App)】的数据库操作Mapper
* @createDate 2026-01-02 12:41:31
* @Entity com.leikooo.codemother.model.entity.App
*/
public interface AppMapper extends BaseMapper<App> {

    String getAppNameById(@Param("id") Long id);
}




