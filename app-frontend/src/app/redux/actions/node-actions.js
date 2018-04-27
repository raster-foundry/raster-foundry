import _ from 'lodash';
import { Map } from 'immutable';

import {authedRequest} from '_api/authentication';

export const NODE_PREVIEWS = 'NODE_PREVIEWS';
export const NODE_SET_ERROR = 'NODE_SET_ERROR';
export const NODE_SET = 'NODE_SET';
// does not affect rendering
export const NODE_UPDATE_SOFT = 'NODE_UPDATE_SOFT';
// force re-render of previews
export const NODE_UPDATE_HARD = 'NODE_UPDATE_HARD';

export const NODE_CREATE = 'NODE_CREATE';

export const NODE_LINK = 'NODE_LINK';


export const NODE_ACTION_PREFIX = 'NODE';

import { astFromNodes, astFromRootNode, nodesFromAnalysis, nodeIsChildOf } from '../node-utils';

// Node ActionCreators

export function startNodePreview() {
    return {
        type: `${NODE_PREVIEWS}_START_PREVIEW`
    };
}

export function startNodeCompare() {
    return {
        type: `${NODE_PREVIEWS}_START_COMPARE`
    };
}

export function selectNode(nodeId) {
    return {
        type: `${NODE_PREVIEWS}_SELECT_NODE`,
        nodeId
    };
}

export function compareNodes(nodes) {
    return {
        type: `${NODE_PREVIEWS}_COMPARE_NODES`,
        nodes
    };
}

export function cancelNodeSelect() {
    return {
        type: `${NODE_PREVIEWS}_CANCEL_SELECT`
    };
}

export function setNodeError({nodeId, error}) {
    return {
        type: NODE_SET_ERROR,
        nodeId,
        payload: error
    };
}

export function updateNode({payload, hard = false}) {
    // TODO handle multiple updates close to each other better
    //      currently histograms do not reflect reality if you
    //      update a second time before the histogram finished fetching.
    //      maybe store initial request times and compare that way, instead of
    //      finish times
    return (dispatch, getState) => {
        let state = getState();
        let labState = getState().lab;
        let promise;
        let updatedNode = payload;
        // TODO update this
        let updatedAnalysis = astFromNodes(labState, updatedNode);

        promise = authedRequest({
            method: 'put',
            url: `${state.api.apiUrl}/api/analyses/${updatedAnalysis.id}`,
            data: updatedAnalysis
        }, getState()).then(() => {
            return updatedAnalysis;
        });

        dispatch({
            type: hard ? NODE_UPDATE_HARD : NODE_UPDATE_SOFT,
            meta: {
                nodes: new Map([[updatedNode.id, updatedNode]]),
                analysis: updatedAnalysis
            },
            payload: promise
        });
    };
}

export function setNodes(payload) {
    return {
        type: NODE_SET, payload
    };
}

export function startCreatingNode(nodeType) {
    return {
        type: `${NODE_CREATE}_START`,
        payload: {nodeType}
    };
}

export function stopCreatingNode() {
    return {
        type: `${NODE_CREATE}_CLOSE`
    };
}

export function cancelCreatingNode() {
    return {
        type: `${NODE_CREATE}_CANCEL`
    };
}

export function selectNodeToCreate(analysis) {
    return {
        type: `${NODE_CREATE}_SELECT`,
        payload: analysis
    };
}

export function finishCreatingNode(coordinates) {
    return (dispatch, getState) => {
        let state = getState();
        let workspace = _.clone(state.lab.workspace);
        let analysisCreate = _.clone(state.lab.createNodeSelection);

        let promise = authedRequest({
            method: 'post',
            url: `${state.api.apiUrl}/api/workspaces/${workspace.id}/analyses`,
            data: analysisCreate
        }, getState());

        dispatch({
            type: `${NODE_CREATE}_FINISH`,
            payload: promise,
            meta: {coordinates}
        });
    };
}

/* TODO
  We need to define analyses by their root nodes, because we need to handle the
  case where input nodes are a part of multiple analyses.

  Roots, defined as any node which is not an input to another node,
  can be created whenever a link is deleted, but it needs to be checked.

  initialization also needs to be updated to support this.

  Nodes need to be updated to contain a list of analyses
  they are a part of instead of just a single one
 */
export function splitAnalysis(childNodeId, parentNodeId) {
    return (dispatch, getState) => {
        let state = getState();
        let workspace = state.lab.workspace;
        let nodes = state.lab.nodes;
        let childNode = _.clone(nodes.get(childNodeId));
        let parentNode = _.clone(nodes.get(parentNodeId));
        let parentAnalysisRootNode = nodes.find((node) => {
            return node.analysisId === parentNode.analysisId && !node.parent;
        });
        let analysisId = childNode.analysisId;
        let analysis = _.clone(workspace.analyses.find((a) => a.id === analysisId));

        // remove child node as argument of parent
        parentNode.args = parentNode.args.filter((id) => id !== childNodeId);

        // create new analysis from the child node and its children
        let childAst = astFromRootNode(nodes, childNode);
        let parentAst = astFromRootNode(
            nodes.set(parentNodeId, parentNode), parentAnalysisRootNode
        );

        let updatedAnalysis = _.clone(analysis);
        updatedAnalysis.executionParameters = parentAst;

        let updatedAnalysisNodes = nodesFromAnalysis(updatedAnalysis);

        let newAnalysis = _.omit(analysis, [
            'id', 'createdAt', 'createdBy', 'modifiedAt', 'modifiedBy'
        ]);
        Object.assign(newAnalysis, {
            executionParameters: childAst,
            name: childNode.name
        });


        // update old analysis
        const updatePromise = authedRequest({
            method: 'put',
            url: `${state.api.apiUrl}/api/analyses/${updatedAnalysis.id}`,
            data: updatedAnalysis
        }, state);
        dispatch({
            type: NODE_UPDATE_HARD,
            meta: {
                nodes: updatedAnalysisNodes,
                analysis: updatedAnalysis
            },
            payload: updatePromise
        });

        // create new analysis
        const createPromise = authedRequest({
            method: 'post',
            url: `${state.api.apiUrl}/api/workspaces/${workspace.id}/analyses`,
            data: newAnalysis
        }, state);
        dispatch({
            type: NODE_UPDATE_HARD,
            meta: {
                nodes: new Map([[parentNode.id, parentNode]]),
                analysis: newAnalysis
            },
            payload: createPromise
        });
    };
}

// TODO Deleting nodes does NOT work atm
export function deleteNode(deleteNodeId) {
    return (dispatch, getState) => {
        let state = getState();
        let workspace = state.lab.workspace;
        let nodes = state.lab.nodes;
        let nodeToDelete = nodes.get(deleteNodeId);

        let parentAnalysis = workspace.analyses.find((a) => a.id === nodeToDelete.analysisId);

        // let parentNodes = nodes.filter((node) => node.args.includes(deleteNodeId));
        let childNodes = nodeToDelete.args.map((id) => nodes.get(id));
        let updatedNodes = nodes;

        if (childNodes.length) {
            let updatedChildNodes = childNodes
                .map((node) => _.clone(node))
                .map((node) => _.remove(node.args, id => id === 'deleteNodeId'));
            updatedChildNodes.forEach(node => {
                updatedNodes = updatedNodes.set(node.id, node);
            });
            updatedNodes = updatedNodes.delete(deleteNodeId);

            let childParents = updatedChildNodes
                .map((node) => ({
                    node,
                    parents: updatedNodes.filter(updatedNode => updatedNode.args.includes(node.id))
                }));

            let childrenNoParents = childParents
                .filter(child => child.parents.length === 0)
                .map(c => c.node);
            let childAsts = childrenNoParents
                .map(node => astFromRootNode(updatedNodes, node));


            let childAnalysisTemplate = _.omit(
                parentAnalysis,
                ['id', 'createdAt', 'createdBy', 'modifiedAt', 'modifiedBy', 'executionParameters']
            );

            // create new analyses starting at the children
            childAsts.forEach((ast) => {
                let newAnalysis = Object.assign({}, childAnalysisTemplate, {
                    executionParameters: ast,
                    name: ''
                });
                // create new analysis
                const createPromise = authedRequest({
                    method: 'post',
                    url: `${state.api.apiUrl}/api/workspaces/${workspace.id}/analyses`,
                    data: newAnalysis
                }, state);
                dispatch({
                    type: NODE_UPDATE_HARD,
                    meta: {
                        nodes: new Map(),
                        analysis: newAnalysis
                    },
                    payload: createPromise
                });
            });
        }

        // TODO update analysis that contained the deleted node
        let deletedNodeRoot = nodes.find((node) => {
            return node.analysisId === nodeToDelete.analysisId && !node.parent;
        });
        if (deletedNodeRoot.id === nodeToDelete.id) {
            authedRequest({
                method: 'delete',
                url: `${state.api.apiUrl}/api/analyses/${nodeToDelete.analysisId}`
            }, state);
        } else {
            let updatedAnalysis = Object.assign({}, parentAnalysis, {
                executionParameters: astFromRootNode(updatedNodes, deletedNodeRoot)
            });
            let updatePromise = authedRequest({
                method: 'put',
                url: `${state.api.apiurl}/api/analyses/${updatedAnalysis.id}`
            });
            dispatch({
                type: NODE_UPDATE_HARD,
                meta: {
                    nodes: new Map(),
                    deleteNodes: [deleteNodeId],
                    analysis: updatedAnalysis
                },
                payload: updatePromise
            });
        }
    };
}


export function startLinkingNodes(nodeId) {
    return {
        type: `${NODE_LINK}_START`,
        payload: nodeId
    };
}

export function cancelLinkingNodes() {
    return {
        type: `${NODE_LINK}_FINISH`
    };
}

export function finishLinkingNodes(linkedNodeId) {
    return (dispatch, getState) => {
        let state = getState();
        let workspace = state.lab.workspace;
        let nodes = state.lab.nodes;
        let inputNodeId = state.lab.linkNode;
        if (!inputNodeId || !linkedNodeId) {
            throw new Error(
                'Tried to link nodes when one wasn\'t defined', inputNodeId, linkedNodeId
            );
        }
        let inputNode = _.clone(nodes.get(inputNodeId));
        let linkedNode = _.clone(nodes.get(linkedNodeId));


        let linkedAnalysisId = linkedNode.analysisId;
        let inputAnalysisId = inputNode.analysisId;

        let linkedAnalysis = _.clone(workspace.analyses.find((a) => a.id === linkedAnalysisId));

        // check if in the same analysis.
        // If so, need to prevent loops in the dag. otherwise don't worry about it
        if (linkedAnalysisId === inputAnalysisId) {
            if (nodeIsChildOf(linkedAnalysisId, inputNodeId, nodes)) {
                throw new Error('Tried to link node to a child of itself');
            }
        } else {
            // delete child analysis
            authedRequest({
                method: 'delete',
                url: `${state.api.apiUrl}/api/analyses/${inputAnalysisId}`
            }, state);
        }

        linkedNode.args = linkedNode.args.concat(inputNode.id);

        let updatedNodes = nodes.set(linkedNode.id, linkedNode);

        let linkedAnalysisRootNode = updatedNodes.find((node) => {
            return node.analysisId === linkedNode.analysisId && !node.parent;
        });

        let updatedAst = astFromRootNode(updatedNodes, linkedAnalysisRootNode);

        let updatedAnalysis = Object.assign(
            {}, linkedAnalysis, { executionParameters: updatedAst }
        );

        let linkedAnalysisNodes = nodesFromAnalysis(updatedAnalysis);

        // update existing analysis
        let updatePromise = authedRequest({
            method: 'put',
            url: `${state.api.apiUrl}/api/analyses/${updatedAnalysis.id}`,
            data: updatedAnalysis
        }, state);
        dispatch({
            type: `${NODE_LINK}_FINISH`
        });
        dispatch({
            type: NODE_UPDATE_HARD,
            meta: {
                nodes: linkedAnalysisNodes,
                analysis: updatedAnalysis,
                deletedAnalysis: inputAnalysisId
            },
            payload: updatePromise
        });
    };
}

export default {
    startNodePreview, startNodeCompare, selectNode, cancelNodeSelect, compareNodes,
    setNodeError, updateNode, setNodes,
    startCreatingNode, stopCreatingNode, cancelCreatingNode, selectNodeToCreate, finishCreatingNode,
    splitAnalysis, deleteNode, startLinkingNodes, cancelLinkingNodes, finishLinkingNodes
};
