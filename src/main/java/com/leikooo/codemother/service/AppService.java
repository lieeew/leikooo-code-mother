package com.leikooo.codemother.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leikooo.codemother.model.dto.AppQueryDto;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author leikooo
 * @description 针对表【app(App)】的数据库操作Service
 * @createDate 2026-01-02 12:41:31
 */
public interface AppService extends IService<App> {
    /**
     * 创建 App
     *
     * @param creatAppDto creatAppDto
     * @return App
     */
    Long createApp(CreatAppDto creatAppDto);

    /**
     * 生成代码
     * @param genAppDto genAppDto
     * @return
     */
    Flux<String> genAppCode(GenAppDto genAppDto);

    /**
     * 获取 AppVO
     * @param id id
     * @return AppVO
     */
    AppVO getAppVO(Long id);

    /**
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper<App> getQueryWrapper(AppQueryDto appQueryDto);

    /**
     *
     * @return lists
     */
    List<AppVO> getAppVOList(Page<App> appPage);
}
