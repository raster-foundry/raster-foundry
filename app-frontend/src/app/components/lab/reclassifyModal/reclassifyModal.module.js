import angular from 'angular';
import _ from 'lodash';

import reclassifyModalTpl from './reclassifyModal.html';

import { getNodeDefinition, getNodeHistogram } from '_redux/node-utils';
import HistogramActions from '_redux/actions/histogram-actions';

const ReclassifyModalComponent = {
    templateUrl: reclassifyModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'ReclassifyModalController'
};

class ReclassifyModalController {
    constructor($scope, $ngRedux, reclassifyService) {
        'ngInject';
        this.reclassifyService = reclassifyService;
        this.breaks = this.resolve.breaks;
        this.nodeId = this.resolve.nodeId;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            HistogramActions
        )(this);
        $scope.$on('$destroy', unsubscribe);

        $scope.$watch('$ctrl.inputNodeId', (id) => {
            if (id) {
                this.fetchHistogram(id);
            }
        });
    }

    mapStateToThis(state) {
        const node = getNodeDefinition(state, this);
        const inputNodeId = _.first(node.args);
        const histogram = getNodeHistogram(state, {nodeId: inputNodeId});
        return {
            node,
            inputNodeId,
            histogram
        };
    }

    $onInit() {
        this.noGapsOverlaps = true;
        this.allEntriesValid = true;

        // Give the table a new object to break update cycles
        this.updateBreaks(this.breaks);
    }

    get classCount() {
        if (this.breaks) {
            return this.breaks.length;
        }
        return 0;
    }

    set classCount(newClassCount) {
        if (!newClassCount || newClassCount === this.classCount) {
            // Don't do anything on invalid numbers or if the number hasn't changed
            return;
        }
        if (newClassCount > this.classCount) {
            // Add more empty classification slots
            let breaksToAdd = newClassCount - this.classCount;
            let lastBreak = this.breaks.slice(-1)[0];

            // Scale the step size to the histogram range if possible
            let step = this.histogram ?
                Math.pow(10,
                    Math.round(
                        Math.log10((this.histogram.maximum - this.histogram.minimum) / 100)
                    )
                ) :
                1;

            // Fill them with values above the highest break
            let newBreaks = Array(breaksToAdd).fill(1).map(
                (a, index) => (index + 1) * step + Number(lastBreak.break)
            );

            this.updateBreaks([
                ...this.breaks,
                ...newBreaks.map(b => {
                    return {
                        break: b,
                        start: lastBreak.break,
                        value: 'NODATA'
                    };
                })
            ]);
        } else if (newClassCount < this.classCount) {
            this.updateBreaks(this.breaks.slice(0, newClassCount));
        }
    }

    equalInterval() {
        if (this.histogram) {
            const min = this.histogram.minimum;
            const max = this.histogram.maximum;
            const rangeWidth = (max - min) / this.classCount;

            // Keep each range's associated output value so that output values stay in the same
            // order when applying the equal interval, but overwrite the range values.
            this.updateBreaks(this.breaks.map((b, i) => {
                return Object.assign({}, b, {
                    break: rangeWidth * (i + 1)
                });
            }));
        }
    }

    updateBreaks(breaks) {
        const alignedBreaks = this.reclassifyService.alignBreakpoints(breaks);
        if (!_.isEqual(alignedBreaks, this.breaks)) {
            this.breaks = angular.copy(alignedBreaks);
            this.displayClassifications = angular.copy(alignedBreaks);
        }
    }

    closeAndUpdate() {
        this.close({$value: this.breaks});
    }
}
const ReclassifyModalModule = angular.module('components.lab.reclassifyModal', []);

ReclassifyModalModule.controller('ReclassifyModalController', ReclassifyModalController);
ReclassifyModalModule.component('rfReclassifyModal', ReclassifyModalComponent);

export default ReclassifyModalModule;
