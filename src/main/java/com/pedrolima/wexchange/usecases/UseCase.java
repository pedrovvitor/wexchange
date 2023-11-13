package com.pedrolima.wexchange.usecases;

public abstract class UseCase<IN, OUT> {

    public abstract OUT execute(IN anIn);
}
