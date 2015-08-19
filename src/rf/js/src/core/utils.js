'use strict';

function asset(relPath) {
    return '/static/' + relPath;
}

module.exports = {
    asset: asset
};
