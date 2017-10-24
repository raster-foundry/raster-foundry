import _ from 'lodash';
import {Map} from 'immutable';

export function toolFromNodes(labState, updatedNode) {
    let tool = labState.tool;
    let nodes = labState.nodes;
    let root = Object.assign({}, _.first(nodes.filter((node) => !node.parent).toArray()));
    let stack = [root];
    while (stack.length) {
        let currentNode = stack.pop();
        if (currentNode.id === updatedNode.id) {
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

    return Object.assign({}, tool, {executionParameters: root});
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

export function nodesFromTool(tool) {
    let nodes = new Map();
    let json = Object.assign({}, tool.executionParameters ? tool.executionParameters : tool);
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
