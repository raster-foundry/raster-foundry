import _ from 'lodash';
const STRIP_COMMENTS = /((\/\/.*$)|(\/\*[\s\S]*?\*\/))/mg;
const ARGUMENT_NAMES = /([^\s,]+)/g;
function getParamNames(func) {
    const fnStr = func.toString().replace(STRIP_COMMENTS, '');
    let result = fnStr.slice(
        fnStr.indexOf('(') + 1,
        fnStr.indexOf(')')
    ).match(ARGUMENT_NAMES);
    if (result === null) {
        result = [];
    }
    return result;
}

const autoInject = (module) => {
    _.forEach(module.resolve, (fn) => {
        fn.$inject = getParamNames(fn);
    });
};

export default autoInject;
