package com.amazonaws.mobileconnectors.appsync;

import android.util.Log;

import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.interceptor.ApolloInterceptor;
import com.apollographql.apollo.interceptor.ApolloInterceptorChain;

import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

class AppSyncRetryOfflineMutationInterceptor implements ApolloInterceptor {

    private static final String TAG = AppSyncRetryOfflineMutationInterceptor.class.getSimpleName();

    private AppSyncOfflineMutationInterceptor.QueueUpdateHandler mQueueUpdateHandler;


    public AppSyncRetryOfflineMutationInterceptor(AppSyncOfflineMutationInterceptor.QueueUpdateHandler queueUpdateHandler) {
        mQueueUpdateHandler = queueUpdateHandler;
    }

    @Override
    public void interceptAsync(@Nonnull InterceptorRequest request, @Nonnull ApolloInterceptorChain chain, @Nonnull Executor dispatcher, @Nonnull final CallBack callBack) {
        chain.proceedAsync(request, dispatcher, new CallBack() {

            @Override
            public void onResponse(@Nonnull InterceptorResponse response) {
                if (shouldRetry(getErrorType(response))) {
                    mQueueUpdateHandler.setMutationInProgressStatusToFalse();
                    return;
                }
                callBack.onResponse(response);
            }

            @Override
            public void onFetch(FetchSourceType sourceType) {
                callBack.onFetch(sourceType);
            }

            @Override
            public void onFailure(@Nonnull ApolloException e) {
                Log.v(TAG, "Thread:[" + Thread.currentThread().getId() + "]: ApolloHttpException " + e.getLocalizedMessage());
                mQueueUpdateHandler.setMutationInProgressStatusToFalse();
            }

            @Override
            public void onCompleted() {

            }
        });
    }

    @Override
    public void dispose() {

    }

    private String getErrorType(@Nonnull ApolloInterceptor.InterceptorResponse response) {
        try {
            List errors = response.parsedResponse.get().errors();
            Error error = (Error) errors.get(0);
            String errorType = (String) error.customAttributes().get("errorType");
            return errorType;
        } catch (Exception e) {
//            e.printStackTrace();
            return null;
        }
    }

    public static boolean shouldRetry(String errorType) {
        if ("DynamoDB:ProvisionedThroughputExceededException".equals(errorType)) {
            return true;
        } else {
            // DynamoDB:ConditionalCheckFailedException
            // unique ID

            // DynamoDB:AmazonDynamoDBException
            // item size > 400KB
            // parameter value > 1KB
            return false;
        }
    }
}
