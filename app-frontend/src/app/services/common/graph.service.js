/* globals console */
import _ from 'lodash';
import {Map, Set} from 'immutable';
import * as d3 from 'd3';
import $ from 'jquery';

const defaultD3Options = (el) => ({
    style: {
        height: '100px'
    },
    height: 100,
    width: el.clientWidth || 100,
    margin: {
        top: 5,
        bottom: 5,
        left: 0,
        right: 0
    }
});

class D3Element {
    constructor(el, id, options) {
        this.el = el;
        this.id = id;
        this.options = options;
        this.callbacks = new Map();

        this.options = Object.assign({}, defaultD3Options(el), options);

        this.initSvg();
    }


    initSvg() {
        $(this.el).css({
            height: this.options.style.height,
            width: '100%'
        });
        return this;
    }

    setData(data) {
        this.data = data;
        return this;
    }

    setOptions(options) {
        this.options = Object.assign(defaultD3Options(this.el), options);
        return this;
    }

    render() {
        this.callbacks.forEach((callback) => {
            callback();
        });
        return this;
    }
}

class ChannelHistogram extends D3Element {
    render() {
        let svg = d3.select(this.el);
        let defs = svg.select('defs');
        if (!defs.nodes().length) {
            defs = svg.append('defs');
        }

        if (!this.data || !Number.isFinite(this.data.maxY)) {
            //eslint-disable-next-line
            console.log('tried to render graph without a maxY');
            return;
        }

        this.calculateAxis(svg, defs);
        switch (this.options.channel) {
        case 'rgb':
            this.renderPlot(svg, defs, this.data.rgb, '#959cad');
            break;
        case 'red':
            this.renderPlot(svg, defs, this.data.red, '#ed1841');
            break;
        case 'green':
            this.renderPlot(svg, defs, this.data.green, '#28af5f');
            break;
        case 'blue':
            this.renderPlot(svg, defs, this.data.blue, '#206fed');
            break;
        default:
            throw new Error(
                `Invalid channel selected in ChannelHistogram options: ${this.options.channel}`
            );
        }
        super.render();
    }

    calculateAxis(svg, defs) {
        const xRange = [this.options.margin.left,
                        this.el.width.baseVal.value - this.options.margin.right];
        this.xScale = d3.scaleLinear()
            .domain([0, 255])
            .range(xRange);
        let logScale = d3.scaleLog()
            .domain([0.01, this.data.maxY])
            .nice()
            .range([this.options.height - this.options.margin.bottom,
                    this.options.margin.top]);
        this.yScale = (x) => logScale(x > 0 ? x : 0.01);
    }

    renderPlot(svg, defs, plot, color) {
        let area = d3.area()
            .x(v => this.xScale(v.x))
            .y0(this.options.height - this.options.margin.bottom)
            .y1(v => this.yScale(v.y))
            .curve(d3.curveStepAfter);


        let areaPath = svg.select('#area-path');
        if (!areaPath.nodes().length) {
            areaPath = svg.append('path');
        }

        areaPath.data([plot])
            .attr('id', 'area-path')
            .attr('class', 'data-fill')
            .attr('z-index', 11)
            .attr('stroke', color)
            .attr('fill', color)
            .attr('d', area);
    }
}

class SinglebandHistogram extends D3Element {
    render() {
        // render d3 el
        let svg = d3.select(this.el);
        let defs = svg.select('defs');
        if (!defs.nodes().length) {
            defs = svg.append('defs');
        }

        if (this.data && this.data.histogram && this.data.breakpoints) {
            this.calculateAxis(svg, defs);
            this.renderData(svg, defs);
            this.renderGradient(svg, defs);
        }

        return super.render();
    }

    calculateAxis(svg, defs) {
        const xRange = [this.options.margin.left,
                        this.el.width.baseVal.value - this.options.margin.right];
        this.xScale = d3.scaleLinear()
            .domain([d3.min(this.data.histogram, d => d.x),
                     d3.max(this.data.histogram, d => d.x)])
            .range(xRange);
        let logScale = d3.scaleLog()
            .domain([0.01, d3.max(this.data.histogram, d => d.y)])
            .nice()
            .range([this.options.height - this.options.margin.bottom,
                    this.options.margin.top]);
        this.yScale = (x) => logScale(x > 0 ? x : 0.01);
    }

    renderData(svg, defs) {
        let areaShadow = d3.area()
            .x(v => this.xScale(v.x))
            .y0(this.options.height - this.options.margin.bottom)
            .y1(v => this.yScale(v.y))
            .curve(d3.curveStepAfter);

        let shadowPath = svg.select('#shadow-path');
        if (!shadowPath.nodes().length) {
            shadowPath = svg.append('path');
        }

        shadowPath.data([this.data.histogram])
            .attr('id', 'shadow-path')
            .attr('z-index', 10)
            .attr('fill', 'rgba(1,1,1,0.15)')
            .attr('stroke', 'rgba(1,1,1,0.25)')
            .attr('d', areaShadow);

        let area = d3.area()
            .x(v => this.xScale(v.x))
            .y0(this.options.height - this.options.margin.bottom)
            .y1(v => this.yScale(v.y))
            .curve(d3.curveStepAfter);


        let areaPath = svg.select('#area-path');
        if (!areaPath.nodes().length) {
            areaPath = svg.append('path');
        }

        areaPath.data([this.data.histogram])
            .attr('id', 'area-path')
            .attr('class', 'data-fill')
            .attr('z-index', 11)
            .attr('d', area);
    }

    renderGradient(svg, defs) {
        let linearGradient = svg.selectAll('linearGradient');
        if (!linearGradient.nodes().length) {
            linearGradient = defs.append('linearGradient');
            linearGradient.attr('id', `line-gradient-${this.id}`)
                .attr('gradientUnits', 'userSpaceOnUse')
                .attr('x1', '0%').attr('y1', 0)
                .attr('x2', '100%').attr('y2', 0);
        }

        let colorData = this.calculateGradientColors();

        linearGradient.selectAll('stop')
            .data([]).exit().remove();
        linearGradient.selectAll('stop')
            .data(colorData)
            .enter()
            .append('stop')
            .attr('offset', (d) => d.offset)
            .attr('stop-color', (d) => d.color)
            .attr('stop-opacity', (d) => Number.isFinite(d.opacity) ? d.opacity : 1.0);
    }

    calculateGradientColors() {
        let max = d3.max(this.data.histogram, d => d.x);
        let min = d3.min(this.data.histogram, d => d.x);
        let range = max - min;
        let data = this.data.breakpoints.map((bp) => {
            let offset = (bp.value - min) / range * 100;
            return {offset, color: bp.color};
        }).sort((a, b) => a.offset - b.offset).map((bp) => {
            return {offset: `${bp.offset}%`, color: bp.color};
        });

        if (this.options.baseScheme && this.options.baseScheme.colorBins > 0) {
            let offsetData = data.map((currentValue, index, array) => {
                if (index !== array.length - 1) {
                    return {offset: array[index + 1].offset, color: currentValue.color};
                }
                return currentValue;
            });
            data = _.flatten(_.zip(data, offsetData));
        }

        if (_.get(this.options, 'masks.min') || this.options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this.options.discrete) {
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
                data.splice(0, 0, {offset: data[0].offset, color: '#353C58'});
            } else {
                data.splice(0, 0, {offset: data[0].offset, color: _.first(data.color)});
                data.splice(0, 0, {offset: data[0].offset, color: last.color});
            }
        }


        if (_.get(this.options, 'masks.max') || this.options.discrete) {
            let last = _.last(data);
            if (last.color === 'NODATA' || !this.options.discrete) {
                data.push({offset: _.last(data).offset, color: '#353C58'});
                data.push({offset: _.last(data).offset, color: '#353C58'});
            } else {
                data.push({offset: _.last(data).offset, color: last.color});
            }
        }

        return data;
    }
}

export default (app) => {
    class GraphService {
        constructor($q) {
            'ngInject';
            this.graphs = new Map();
            this._graphPromises = new Map();
            this.$q = $q;
        }

        register(el, id, options = {}) {
            // el is expected to be a bare DOM svg node
            if (el.nodeName !== 'svg') {
                throw new Error('graphService requires an svg element.');
            }
            let graph;
            switch (options.type) {
            case 'channel':
                graph = new ChannelHistogram(el, id, options);
                break;
            case 'single':
            default:
                graph = new SinglebandHistogram(el, id, options);
            }
            this.graphs = this.graphs.set(id, graph);
            if (this._graphPromises.has(id)) {
                this._graphPromises.get(id).forEach((promise) => {
                    promise.resolve(graph);
                });
                this._graphPromises.delete(id);
            }
            return graph;
        }

        deregister(id) {
            this.getGraph(id).then((graph) => {
                if (this._graphPromises.has(id)) {
                    this._graphPromises.get(id).forEach((promise) => {
                        promise.reject('Graph has been deleted');
                    });
                }
                graph.callbacks.forEach((value, key) => {
                    graph.off(key);
                });
            });
            this.graphcs = this.graphs.delete(id);
        }

        getGraph(id) {
            return this.$q((resolve, reject) => {
                if (this.graphs.has(id)) {
                    resolve(this.graphs.get(id));
                } else if (this._graphPromises.has(id)) {
                    const promises = this._graphPromises.get(id);
                    promises.push({resolve, reject});
                    this._graphPromises = this._graphPromises.set(id, promises);
                } else {
                    this._graphPromises = this._graphPromises.set(id, [{resolve, reject}]);
                }
            });
        }
    }
    app.service('graphService', GraphService);
};
