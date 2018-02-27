import _ from 'lodash';
import {Map} from 'immutable';

// TODO fix this - it's returning empty objects for nodes
export function astFromNodes(labState, updatedNode) {
    let analysis = _.find(labState.workspace.analyses, (a) => a.id === updatedNode.analysisId);
    let nodes = labState.nodes;
    let root = _.omit(
        _.first(
            nodes.filter(
                (node) => !node.parent && node.analysisId === updatedNode.analysisId
            ).toArray()
        ), 'analysisId');

    let stack = [root];
    while (stack.length) {
        let currentNode = stack.pop();
        delete currentNode.analysisId;
        if (currentNode.id === updatedNode.id) {
            let parent = currentNode.parent;
            currentNode = _.omit(updatedNode, 'analysisId');
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

export function astFromAnalysisNodes(analysis, nodes) {
    let root = nodes.find((n) => !n.parent);
    let stack = [root];

    while (stack.length) {
        let currentNode = stack.pop();
        delete currentNode.analysisId;
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

export function astFromRootNode(nodes, root) {
    let rootNode = _.clone(root);
    let stack = [rootNode];

    while (stack.length) {
        let currentNode = stack.pop();
        delete currentNode.analysisId;
        delete currentNode.parent;
        if (currentNode.args && currentNode.args.length) {
            currentNode.args = currentNode.args.map(arg => {
                let node = Object.assign({}, nodes.get(arg), {parent: currentNode});
                return node;
            });
            stack = stack.concat(currentNode.args);
        }
    }

    return rootNode;
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

export function nodesFromAnalysis(analysis) {
    let nodes = new Map();
    let json = _.cloneDeep(analysis.executionParameters);
    let stack = [json];
    while (stack.length) {
        let node = Object.assign({}, stack.pop());
        let args = getNodeArgs(node);
        Object.assign(node, {args: args.map(arg => arg.id)}, {analysisId: analysis.id});
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

export function nodeIsChildOf(linkedNodeId, inputNodeId, nodes) {
    if (linkedNodeId === inputNodeId) {
        return true;
    }
    let stack = [nodes.get(inputNodeId)];
    while (stack.length) {
        let node = stack.pop();
        let numArgs = node.args && node.args.length ? node.args.length : 0;
        for (let i = 0; i < numArgs; i = i + 1) {
            let argId = node.args[i];
            if (argId === linkedNodeId) {
                return true;
            }
            stack.push(nodes.get(argId));
        }
    }
    return false;
}
