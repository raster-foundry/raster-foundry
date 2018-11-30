import _ from 'lodash';
import {Map} from 'immutable';
import {Promise} from 'es6-promise';
import {authedRequest} from '../api/authentication';
import {colorStopsToRange} from '_redux/histogram-utils';
import {createRenderDefinition} from '_redux/histogram-utils';

export function astFromNodes(labState, updatedNodes) {
    const analysis = labState.analysis;
    const nodes = labState.nodes;
    let root = Object.assign({}, _.first(nodes.filter((node) => !node.parent).toArray()));
    let stack = [root];
    const updatedNodeMap = updatedNodes.reduce((m, node) => m.set(node.id, node), new Map());
    while (stack.length) {
        let currentNode = stack.pop();
        if (updatedNodeMap.has(currentNode.id)) {
            let updatedNode = updatedNodeMap.get(currentNode.id);
            let parent = currentNode.parent;
            currentNode = Object.assign({}, updatedNode);
            if (root.id === currentNode.id) {
                root = currentNode;
            }
            if (parent) {
                let index = parent.args.findIndex((node) => node.id === currentNode.id);
                parent.args[index] = currentNode;
            }
        }
        delete currentNode.parent;
        if (currentNode.args && currentNode.args.length) {
            currentNode.args = currentNode.args.map(arg => {
                let node = Object.assign({}, nodes.get(arg), {parent: currentNode});
                return node;
            });
            stack = stack.concat(currentNode.args);
        }
    }

    return Object.assign({}, analysis, {executionParameters: root});
}

export function getNodeArgs(node) {
    if (node.args) {
        if (Array.isArray(node.args)) {
            return [...node.args];
        }
        return Object.values(node.args);
    }
    return [];
}

export function nodesFromAst(ast) {
    let nodes = new Map();
    let json = Object.assign(
        {},
        ast.executionParameters ? ast.executionParameters : ast
    );
    let stack = [json];
    while (stack.length) {
        let node = Object.assign({}, stack.pop());
        let args = getNodeArgs(node);
        Object.assign(node, {args: args.map(arg => arg.id)});
        nodes = nodes.set(node.id, node);
        if (node.args && node.args.length) {
            stack = stack.concat(
                args.map((a) => {
                    return Object.assign({
                        parent: node.id
                    }, a);
                })
            );
        }
    }
    return nodes;
}

export function getNodeDefinition(state, context) {
    if (context.nodeId && state.lab.nodes) {
        return state.lab.nodes.get(context.nodeId);
    }
    return false;
}

export function getNodeHistogram(state, context) {
    if (context.nodeId && state.lab.histograms) {
        return state.lab.histograms.get(context.nodeId);
    }
    return false;
}

export function createNodeMetadata(state, context) {
    return authedRequest({
        method: 'get',
        url: `${state.api.tileUrl}/tools/${state.lab.analysis.id}/histogram` +
            `?node=${context.node.id}&voidCache=true&token=${state.api.apiToken}`
    }, state).then((response) => {
        const histogram = response.data;
        return createRenderDefinition(histogram);
    });
}
