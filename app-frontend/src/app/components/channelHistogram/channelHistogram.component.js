import channelHistogramTpl from './channelHistogram.html';

const channelHistogram = {
    templateUrl: channelHistogramTpl,
    controller: 'ChannelHistogramController',
    bindings: {
        data: '<'
    }
};

export default channelHistogram;
