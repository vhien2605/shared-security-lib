package vdt.mini.shared_lib.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class OutBoundSecurityAspect {

    @Pointcut("@annotation(vdt.mini.shared_lib.annotation.OutBoundSecurity)")
    public void outboundPointcut() {
    }

    @Around("outboundPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }
}
