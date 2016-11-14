import angular from 'angular';

import ChannelHistogram from './channelHistogram.component.js';
import ChannelHistogramController from './channelHistogram.controller.js';

const ChannelHistogramModule = angular.module('components.channelHistogram', []);

ChannelHistogramModule.component('rfChannelHistogram', ChannelHistogram);
ChannelHistogramModule.controller('ChannelHistogramController', ChannelHistogramController);

export default ChannelHistogramModule;
