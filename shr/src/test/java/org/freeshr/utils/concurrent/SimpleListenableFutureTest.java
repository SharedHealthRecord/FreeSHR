package org.freeshr.utils.concurrent;

import com.google.common.util.concurrent.SettableFuture;
import org.freeshr.util.ResultHolder;
import org.junit.Test;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class SimpleListenableFutureTest {

    @Test
    public void shouldUseTheAdaptMethodToTransformFutureValue() throws ExecutionException, InterruptedException {
        final ResultHolder resultHolder = new ResultHolder(Boolean.FALSE);
        final String value = "someValue";

        SettableFuture<String> future = SettableFuture.create();
        future.set(value);

        SimpleListenableFuture<Boolean, String> listenableFuture = new SimpleListenableFuture<Boolean, String>(future) {
            @Override
            protected Boolean adapt(String result) throws ExecutionException {
                assertEquals(value, result);
                resultHolder.setCalled(true);
                return true;
            }
        };
        assertTrue(listenableFuture.get());
        assertTrue(resultHolder.getCalled());
    }



    @Test
    public void shouldInvokeTheCallbackWhenTheFutureValueIsResolved() throws ExecutionException, InterruptedException {
        final ResultHolder resultHolder = new ResultHolder(Boolean.FALSE);
        final String value = "someValue";

        SettableFuture<String> future = SettableFuture.create();
        future.set(value);

        SimpleListenableFuture<Boolean, String> listenableFuture = new SimpleListenableFuture<Boolean, String>(future) {
            @Override
            protected Boolean adapt(String result) throws ExecutionException {
                return true;
            }
        };
        listenableFuture.addCallback(new ListenableFutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                resultHolder.setCalled(true);
            }

            @Override
            public void onFailure(Throwable t) {

            }
        });
        assertTrue(listenableFuture.get());
        assertTrue(resultHolder.getCalled());
    }
}