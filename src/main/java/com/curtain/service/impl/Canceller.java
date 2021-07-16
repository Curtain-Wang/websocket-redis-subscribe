package com.curtain.service.impl;

import com.curtain.service.Cancelable;

import java.util.concurrent.ScheduledFuture;

/**
 * @ClassName: Canceller
 * @Description:
 * @Author: 段振宇
 * @Date: 2021/4/14 16:54
 */
public class Canceller implements Cancelable {

    private ScheduledFuture future;

    public Canceller(ScheduledFuture future) {
        this.future = future;
    }

    @Override
    public void cancel() {
        if (! this.isCancelled()) {
            this.future.cancel(false);
        }
    }

    @Override
    public boolean isCancelled() {
        return this.future.isCancelled();
    }
}
