package com.leikooo.codemother.utils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring上下文工具类
 * 用于在静态方法中获取Spring Bean
 */
@Component
public class SpringContextUtil implements BeanFactoryPostProcessor, ApplicationContextAware {

    private static ApplicationContext applicationContext;
    private static ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        SpringContextUtil.applicationContext = ctx;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
        SpringContextUtil.beanFactory = factory;
    }

    // =============================
    //        核心 Bean 获取
    // =============================

    /**
     * 根据类型获取 Bean（强制存在）
     */
    public static <T> T getBean(Class<T> clazz) {
        assertContextInjected();
        return applicationContext.getBean(clazz);
    }

    /**
     * 根据名称获取 Bean（强制存在）
     */
    public static Object getBean(String name) {
        assertContextInjected();
        return applicationContext.getBean(name);
    }

    /**
     * 名称+类型（强制存在）
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        assertContextInjected();
        return applicationContext.getBean(name, clazz);
    }

    // =============================
    //          增强能力
    // =============================

    /**
     * 根据类型获取 Bean（不存在则返回 null，不抛异常）
     */
    public static <T> T getBeanIfExists(Class<T> clazz) {
        assertContextInjected();
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据名称获取 Bean（不存在则 null）
     */
    public static Object getBeanIfExists(String name) {
        assertContextInjected();
        try {
            return applicationContext.getBean(name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取所有某类型 Bean
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        assertContextInjected();
        return applicationContext.getBeansOfType(type);
    }

    /**
     * 获取某类型的 primary Bean（处理同类型多实现的场景）
     */
    public static <T> T getPrimaryBean(Class<T> clazz) {
        assertContextInjected();
        String[] names = beanFactory.getBeanNamesForType(clazz);
        if (names.length == 0) {
            throw new IllegalStateException("没有找到类型 " + clazz.getName() + " 的 Bean");
        }
        if (names.length == 1) {
            return applicationContext.getBean(names[0], clazz);
        }
        // 检查 primary 标记
        for (String name : names) {
            if (beanFactory.getBeanDefinition(name).isPrimary()) {
                return applicationContext.getBean(name, clazz);
            }
        }
        throw new IllegalStateException("找到多个 " + clazz.getName() + " 的 Bean，但没有一个标记 @Primary");
    }

    // =============================
    //        内部工具
    // =============================

    private static void assertContextInjected() {
        if (applicationContext == null) {
            throw new IllegalStateException(
                    "SpringContextUtil 未注入 ApplicationContext，请确认是否被 Spring 扫描到。"
            );
        }
    }
}
