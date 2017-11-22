const asyncDispatchMiddleware = store => next => action => {
    let syncActivityFinished = false;
    let actionQueue = [];

    function flushQueue() {
        actionQueue.forEach(a => store.dispatch(a));
        actionQueue = [];
    }

    function asyncDispatch(asyncAction) {
        actionQueue = actionQueue.concat([asyncAction]);

        if (syncActivityFinished) {
            flushQueue();
        }
    }

    const actionWithAsyncDispatch =
          Object.assign({}, action, { asyncDispatch });

    next(actionWithAsyncDispatch);
    syncActivityFinished = true;
    flushQueue();
};

export default {
    asyncDispatchMiddleware
};
