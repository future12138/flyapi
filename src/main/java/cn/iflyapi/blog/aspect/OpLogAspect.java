package cn.iflyapi.blog.aspect;

import cn.iflyapi.blog.annotation.OpLog;
import cn.iflyapi.blog.dao.UserLogMapper;
import cn.iflyapi.blog.dao.custom.UserCustomMapper;
import cn.iflyapi.blog.entity.UserLog;
import cn.iflyapi.blog.enums.OperationEnum;
import cn.iflyapi.blog.pojo.po.UserFame;
import cn.iflyapi.blog.util.IPUtils;
import cn.iflyapi.blog.util.JwtUtils;
import cn.iflyapi.blog.util.SnowflakeIdWorker;
import com.auth0.jwt.interfaces.Claim;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author flyhero
 * @date 2018-12-21 3:27 PM
 */
@Component
@Aspect
public class OpLogAspect {

    @Autowired
    private UserCustomMapper userCustomMapper;

    @Autowired
    private UserLogMapper userLogMapper;

    @Autowired
    private SnowflakeIdWorker idWorker;

    @Pointcut("@annotation(cn.iflyapi.blog.annotation.OpLog)")
    public void pointcut() {
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        //记录http请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String userIp = IPUtils.getIP(request);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OpLog annotation = method.getAnnotation(OpLog.class);
        //TODO 根据操作限制每日最高分
        OperationEnum operationEnum = annotation.op();
        int score = annotation.score();
        Map<String, Claim> map = getStringClaimMap(request);

        UserLog userLog = new UserLog();
        userLog.setId(idWorker.nextId());
        userLog.setOpType(operationEnum.getCode());
        userLog.setScore(score);
        userLog.setUserAgent(request.getHeader("User-Agent"));

        if (null != map) {
            userLog.setUserId(Long.valueOf(map.get("userId").asString()));
            userLog.setUserNickName(map.get("nickName").asString());
            UserFame userFame = new UserFame();
            userFame.setUserId(userLog.getUserId());
            userFame.setScore(score);
            userCustomMapper.countUserFameVal(userFame);
        }
        userLogMapper.insertSelective(userLog);

        return joinPoint.proceed();
    }

    private Map<String, Claim> getStringClaimMap(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        return JwtUtils.verify(token);
    }
}