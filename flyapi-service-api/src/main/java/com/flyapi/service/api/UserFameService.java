package com.flyapi.service.api;

import com.flyapi.core.base.BaseService;
import com.flyapi.model.UcenterFame;
import com.flyapi.model.UcenterUserFame;

/**
 * author: flyhero
 * Date: 2017/6/9 0009 下午 3:57
 */
public interface UserFameService extends BaseService<UcenterUserFame> {

    int addFameValue(Long userId,Integer opType);
}