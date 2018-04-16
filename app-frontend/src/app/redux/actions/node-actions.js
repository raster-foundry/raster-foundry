/* globals console */
import _ from 'lodash';
import {Promise} from 'es6-promise';
import { Map } from 'immutable';

import {authedRequest} from '_api/authentication';

export const NODE_PREVIEWS = 'NODE_PREVIEWS';
export const NODE_SET_ERROR = 'NODE_SET_ERROR';
export const NODE_SET = 'NODE_SET';
export const NODE_LINKS_SET = 'NODE_LINKS_SET';
export const NODE_UPDATE = 'NODE_UPDATE';

export const NODE_CREATE = 'NODE_CREATE';

export const NODE_LINK = 'NODE_LINK';

export const NODE_ACTION_PREFIX = 'NODE';

import NodeUtils from '../node-utils';

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

export function updateNode(updatedNode) {
    // TODO handle multiple updates close to each other better
    //      currently histograms do not reflect reality if you
    //      update a second time before the histogram finished fetching.
    //      maybe store initial request times and compare that way, instead of
    //      finish times
    return (dispatch, getState) => {
        const state = getState();
        const labState = getState().lab;
        const analysisRoots = labState.analysisRoots;
        const updatedNodes = labState.nodes.set(updatedNode.id, updatedNode);

        const updatedAnalyses = NodeUtils.getNodeRoots(labState.linksBySource, updatedNode.id)
              .map((root) => analysisRoots.findKey((rootId) => rootId === root))
              .map((rootId, analysisId) =>
                   labState.workspace.analyses.find((analysis) => analysisId === analysis.id))
              .map((analysis) => Object.assign({}, analysis, {
                  executionParameters: NodeUtils.astFromRootId(
                      updatedNodes, analysisRoots.get(analysis.id)
                  )}));

        let analysisPromises = updatedAnalyses.map((analysis) => authedRequest({
            method: 'put',
            url: `${state.api.apiUrl}/api/analyses/${analysis.id}`,
            data: analysis
        }, state)).toArray();
        let updatePromise = Promise.all(analysisPromises);

        dispatch({
            type: NODE_UPDATE,
            meta: {
                nodes: updatedNodes,
                analyses: updatedAnalyses
            },
            payload: updatePromise
        });
    };
}

export function setNodes(payload) {
    return {
        type: NODE_SET, payload
    };
}

export function setLinks(linksBySource, linksByTarget) {
    return {
        type: NODE_LINKS_SET, payload: {linksBySource, linksByTarget}
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

        NodeUtils.setAnalysisRelativePositions(
            analysisCreate, coordinates,
            state.lab.onShapeMove
        );
        let {
            nodes, linksBySource, linksByTarget
        } = NodeUtils.analysisToNodesAndLinks(analysisCreate);

        let promise = authedRequest({
            method: 'post',
            url: `${state.api.apiUrl}/api/workspaces/${workspace.id}/analyses`,
            data: analysisCreate
        }, getState());

        nodes = nodes.map(NodeUtils.createNodeShapes());
        NodeUtils.addLinkShapes(nodes, linksByTarget);

        dispatch({
            type: `${NODE_CREATE}_FINISH`,
            payload: promise,
            meta: {
                nodes,
                linksBySource,
                linksByTarget
            }
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
        let childAst = NodeUtils.astFromRootNode(nodes, childNode);
        let parentAst = NodeUtils.astFromRootNode(
            nodes.set(parentNodeId, parentNode), parentAnalysisRootNode
        );

        let updatedAnalysis = _.clone(analysis);
        updatedAnalysis.executionParameters = parentAst;

        let updatedAnalysisNodes = NodeUtils.nodesFromAnalysis(updatedAnalysis);

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
            type: NODE_UPDATE,
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
        d
ispatch({
            type: NODE_UPDATE,
            meta: {
                nodes: new Map([[parentNode.id, parentNode]]),
                analysis: newAnalysis
            },
            payload: createPromise
        });
    };
}

// TODO Deleting nodes does NOT work atm
export function deleteNode(
    // deleteNodeId
) {
    // return (dispatch, getState) => {
    //     let state = getState();
    //     let workspace = state.lab.workspace;
    //     let nodes = state.lab.nodes;
    //     let links = state.lab.links;
    //     let nodeToDelete = nodes.get(deleteNodeId);

    //     let analysesToUpdate = workspace.analyses.filter((a) => nodeToDelete.analyses.has(a.id));
    //     let linksToDelete = links.filter(
    //         (link) => link.source === deleteNodeId || link.target === deleteNodeId
    //     );

    //     // let parentAnalysis = workspace.analyses.find((a) => a.id === nodeToDelete.analysisId);

    //     // let parentNodes = nodes.filter((node) => node.args.includes(deleteNodeId));
    //     let childNodes = nodeToDelete.args.map((id) => nodes.get(id));
    //     let updatedNodes = nodes;

    //     if (childNodes.length) {
    //         let updatedChildNodes = childNodes
    //             .map((node) => _.clone(node))
    //             .map((node) => _.remove(node.args, id => id === 'deleteNodeId'));
    //         updatedChildNodes.forEach(node => {
    //             updatedNodes = updatedNodes.set(node.id, node);
    //         });
    //         updatedNodes = updatedNodes.delete(deleteNodeId);

    //         let childParents = updatedChildNodes
    //             .map((node) => ({
    //                 node,
                    // parents: updatedNodes.filter(
                    //     updatedNode => updatedNode.args.includes(node.id)
                     // )
    //             }));

    //         let childrenNoParents = childParents
    //             .filter(child => child.parents.length === 0)
    //             .map(c => c.node);
    //         let childAsts = childrenNoParents
    //             .map(node => NodeUtils.astFromRootNode(updatedNodes, node));


    //         let childAnalysisTemplate = _.omit(
    //             parentAnalysis,
    //             ['id', 'createdAt', 'createdBy', 'modifiedAt',
    //              'modifiedBy', 'executionParameters']
    //         );

    //         // create new analyses starting at the children
    //         childAsts.forEach((ast) => {
    //             let newAnalysis = Object.assign({}, childAnalysisTemplate, {
    //                 executionParameters: ast,
    //                 name: ''
    //             });
    //             // create new analysis
    //             const createPromise = authedRequest({
    //                 method: 'post',
    //                 url: `${state.api.apiUrl}/api/workspaces/${workspace.id}/analyses`,
    //                 data: newAnalysis
    //             }, state);
    //             dispatch({
    //                 type: NODE_UPDATE,
    //                 meta: {
    //                     nodes: new Map(),
    //                     analysis: newAnalysis
    //                 },
    //                 payload: createPromise
    //             });
    //         });
    //     }

    //     // TODO update analysis that contained the deleted node
    //     let deletedNodeRoot = nodes.find((node) => {
    //         return node.analysisId === nodeToDelete.analysisId && !node.parent;
    //     });
    //     if (deletedNodeRoot.id === nodeToDelete.id) {
    //         authedRequest({
    //             method: 'delete',
    //             url: `${state.api.apiUrl}/api/analyses/${nodeToDelete.analysisId}`
    //         }, state);
    //     } else {
    //         let updatedAnalysis = Object.assign({}, parentAnalysis, {
    //             executionParameters: NodeUtils.astFromRootNode(updatedNodes, deletedNodeRoot)
    //         });
    //         let updatePromise = authedRequest({
    //             method: 'put',
    //             url: `${state.api.apiurl}/api/analyses/${updatedAnalysis.id}`
    //         });
    //         dispatch({
    //             type: NODE_UPDATE,
    //             meta: {
    //                 nodes: new Map(),
    //                 deleteNodes: [deleteNodeId],
    //                 analysis: updatedAnalysis
    //             },
    //             payload: updatePromise
    //         });
    //     }
    // };
}

// TODO finish this once links is a set
export function deleteLink(link) {
    return (dispatch, getState) => {
        let state = getState();
        let {nodes, linksByTarget, linksBySource} = state.lab;
        // NOTE: source is the leaf node,
        // target is the parent (root nodes are targets, but not sources)
        const source = nodes.get(link.source);
        const target = nodes.get(link.target);

        let updatedLinksByTarget;
        let targetLinks = linksByTarget.get(link.target).delete(link.source);
        if (targetLinks.size === 0) {
            updatedLinksByTarget = linksByTarget.set(
                link.target,
                targetLinks
            );
        } else {
            updatedLinksByTarget = linksByTarget.delete(link.target);
        }
        let updatedLinksBySource;
        let sourceLinks = linksBySource.get(link.source).delete(link.target);
        if (sourceLinks.size === 0) {
            updatedLinksBySource = linksBySource.set(
                link.source,
                sourceLinks
            );
        } else {
            updatedLinksBySource = linksBySource.delete(link.source);
        }

        const updatedNodes = nodes.set(target.id, Object.assign({}, target, {
            inputIds: _.remove(target.inputIds, (id) => id === source.id)
        }));

        let apiPromises = [];
        // check if a new root node is created
        let workspace = state.lab.workspace;

        if (sourceLinks.size === 0) {
            let createdAnalysis = {
                name: source.name,
                executionParameters: NodeUtils.astFromRootId(nodes, source.id),
                organizationId: workspace.organizationId,
                visibility: 'PRIVATE'
            };

            let createPromise = authedRequest({
                method: 'post',
                url: `${state.api.apiUrl}/api/workspaces/${state.lab.workspace.id}/analyses`,
                data: createdAnalysis
            }, state).then((response) => {
                return response.data;
            });
            apiPromises.push(createPromise);
        }

        let updatedRoots = NodeUtils.getNodeRoots(updatedLinksBySource, link.target);
        let updatedAnalysisIds = state.lab.analysisRoots
            .filter((rootId) => updatedRoots.has(rootId))
            .keySeq().toArray();
        let updatedAnalyses = updatedAnalysisIds
            .map((analysisId) => {
                let oldAnalysis = workspace.analyses.find((analysis) => analysis.id === analysisId);
                return Object.assign(
                    {},
                    oldAnalysis,
                    {
                        executionParameters: NodeUtils.astFromRootId(
                            updatedNodes, state.lab.analysisRoots.get(analysisId)
                        )
                    }
                );
            });

        apiPromises.concat(updatedAnalyses.map((analysis) => {
            return authedRequest({
                method: 'put',
                url: `${state.api.apiUrl}/api/analyses/${analysis.id}`,
                data: analysis
            }, state).then(() => analysis);
        }));


        dispatch({
            type: `${NODE_LINK}_DELETE`,
            payload: Promise.all(apiPromises),
            meta: {
                linksBySource: updatedLinksBySource,
                linksByTarget: updatedLinksByTarget,
                nodes
            }
        });
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

/*
  Process for linking nodes:
  Add the link.
  update the parent node to include the linked node in its inputIds
  If the node is an analysis root, delete the analysis and remove it from the
  analysisRoots map
 */

export function finishLinkingNodes(targetNodeId) {
    return (dispatch, getState) => {
        let state = getState();
        let labState = state.lab;
        let sourceNodeId = labState.linkNode;
        if (!sourceNodeId || !targetNodeId) {
            throw new Error(
                'Tried to link nodes when one wasn\'t defined', sourceNodeId, targetNodeId
            );
        }
        let nodes = labState.nodes;

        let targetNode = Object.assign({}, nodes.get(targetNodeId));

        if (NodeUtils.nodeIsChildOf(targetNodeId, sourceNodeId, nodes)) {
            throw new Error(`Linking ${sourceNodeId} and ${targetNodeId} would create ` +
                            `a circular dependency because ${sourceNodeId} already ` +
                            `depends on ${targetNodeId}. WorkspaceID = ${state.lab.workspace.id}`);
        }

        // see if the sourceNode is an analysis root
        const sourceNodeAnalysis = labState.analysisRoots.findKey(
            (rootId) => rootId === sourceNodeId
        );
        if (sourceNodeAnalysis) {
            authedRequest({
                method: 'delete',
                url: `${state.api.apiUrl}/api/analyses/${sourceNodeAnalysis}`
            }, state);
        }


        targetNode.inputIds = targetNode.inputIds.slice();
        targetNode.inputIds.push(sourceNodeId);
        // TODO update ports
        let numInputs = targetNode.ports.filter((port) => port.group === 'inputs');
        const port = {
            id: `input-${numInputs}`,
            label: `input-${numInputs}`,
            group: 'inputs'
        };
        targetNode.ports.push(port);
        targetNode.shape.addPort(port);

        let sourceLinks = labState.linksBySource.get(sourceNodeId) || new Map();
        let targetLinks = labState.linksByTarget.get(targetNode) || new Map();
        const link = {source: sourceNodeId, target: targetNodeId};

        let updatedNodes = nodes.set(targetNode.id, targetNode);

        NodeUtils.addLinkShape(updatedNodes, link);

        let updatedLinksBySource = (sourceLinks || new Map())
            .set(sourceNodeId, new Map([[targetNodeId, link]]));
        let updatedLinksByTarget = (targetLinks || new Map())
            .set(targetNodeId, new Map([[sourceNodeId, link]]));

        let updatedAnalyses = NodeUtils.getNodeRoots(updatedLinksBySource, targetNodeId)
            .map((rootId) => {
                let analysisId = labState.analysisRoots.findKey((id) => id === rootId);
                let analysis = _.clone(
                    labState.workspace.analyses.find((a) => a.id === analysisId)
                );
                analysis.executionParameters = NodeUtils.astFromRootId(updatedNodes, rootId);
                return analysis;
            }).toArray();


        // update existing analyses
        let updatePromises = Promise.all(updatedAnalyses.map((analysis) => {
            return authedRequest({
                method: 'put',
                url: `${state.api.apiUrl}/api/analyses/${analysis.id}`,
                data: analysis
            }, state);
        }));
        dispatch({
            type: `${NODE_LINK}_FINISH`
        });
        dispatch({
            type: NODE_UPDATE,
            meta: _.omitBy({
                nodes: updatedNodes,
                linksBySource: updatedLinksBySource,
                linksByTarget: updatedLinksByTarget,
                analyses: updatedAnalyses,
                deletedAnalysis: sourceNodeAnalysis
            }, _.isUndefined),
            payload: updatePromises
        });
    };
}

export default {
    startNodePreview, startNodeCompare, selectNode, cancelNodeSelect, compareNodes,
    setNodeError, updateNode, setNodes, setLinks, deleteLink,
    startCreatingNode, stopCreatingNode, cancelCreatingNode, selectNodeToCreate, finishCreatingNode,
    splitAnalysis, deleteNode, startLinkingNodes, cancelLinkingNodes, finishLinkingNodes
};
