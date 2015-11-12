'use strict';

var $ = require('jquery'),
    _ = require('underscore'),
    React = require('react'),
    Map = require('./map'),
    DropdownMenu = require('./menu').DropdownMenu,
    settings = require('../../settings'),
    mixins = require('../mixins'),
    ProcessingBlock = require('./processing-block');

var Library = React.createBackboneClass({
    componentDidMount: function() {
        this.bindEvents();
    },

    bindEvents: function() {
        // TODO Most of the stuff in this method can be
        // turned into separate callback methods
        // and inline event bindings to be more idiomatic.

        // Tooltips
        $('[data-toggle="tooltip"]').tooltip({
            container: '.sidebar-utility-content',
            viewport: '.sidebar'
        });

        function resize() {
            var sidebar = $('.sidebar'),
                sidebarHeader = $('.sidebar-header'),
                sidebarUtilHeader = $('.sidebar-utility-header'),
                sidebarUtilToolbar = $('.sidebar-utility-toolbar'),
                resizeListGroup = $('.sidebar .list-group'),
                resizeLayerDetails = $('.layer-detail .layer-detail-content, .image-metadata .layer-detail-content'),
                height = sidebar.height() -
                         sidebarHeader.height() -
                         sidebarUtilHeader.height() -
                         sidebarUtilToolbar.height() - 30,
                heightSecondary = sidebar.height() -
                                  sidebarHeader.height() -
                                  sidebarUtilToolbar.height() - 20;
            resizeListGroup.css({'max-height': height + 'px'});
            resizeLayerDetails.css({'max-height': heightSecondary + 'px'});
        }

        resize();
        $(window).resize(resize);
    },

    render: function() {
        return (
            <div>
                <div className="sidebar">
                    <Sidebar {...this.props} />
                </div>
                <Map model={this.props.mapModel} {...this.props} />
                <ProcessingBlock collection={this.props.pendingLayers} />
            </div>
        );
    }
});

var Sidebar = React.createBackboneClass({
    mixins: [
        mixins.LayersMixin()
    ],

    render: function() {
        return (
            <div>
                <DropdownMenu />
                <div className="sidebar-utility" role="tabpanel">
                    <Tabs model={this.props.tabModel} />
                    <div className="sidebar-utility-content">
                        <TabContents model={this.props.tabModel} {...this.props} />
                    </div>
                    <LayerMetadata {...this.props} />
                    <ImageMetadata {...this.props} />
                </div>
            </div>
        );
    }
});

var Tabs = React.createBackboneClass({
    render: function() {
        var activeTab = this.getModel().get('activeTab');
        return (
            <div className="sidebar-utility-header">
                <ul className="nav nav-tabs nav-tabs-dark" role="tablist">
                    <li role="presentation" className={activeTab === 'imports' ? 'active' : ''}
                        ><a href="/imports" data-url="/imports" aria-controls="imports" role="tab">My Imports</a></li>
                    <li role="presentation" className={activeTab === 'catalog' ? 'active' : ''}
                        ><a href="/catalog" data-url="/catalog" aria-controls="catalog" role="tab">Public Catalog</a></li>
                    <li role="presentation" className={activeTab === 'favorites' ? 'active' : ''}
                        ><a href="/favorites" data-url="/favorites" aria-controls="favorites" role="tab">Favorites</a></li>
                </ul>
            </div>
        );
    }
});

var TabContents = React.createBackboneClass({
    render: function() {
        var activeTab = this.getModel().get('activeTab');
        return (
            <div className="tab-content">
                {/* Imports Tab Pane */}
                <LayerCollection collection={this.props.myLayers} id="imports"
                    uploadsEnabled={true} active={activeTab === 'imports'} />
                {/* /#imports.tab-pane */}

                {/* Catalog Tab Pane */}
                <LayerCollection collection={this.props.publicLayers} id="catalog"
                    active={activeTab === 'catalog'} />
                {/* /#catalog.tab-pane */}

                {/* Favorites Tab Pane */}
                <LayerCollection collection={this.props.favoriteLayers} id="favorites"
                    active={activeTab === 'favorites'} />
                {/* /#favorites.tab-pane */}
            </div>
        );
    }
});

var LayerCollection = React.createBackboneClass({
    getInitialState: function() {
        return {
            fetchData: {
                name_search: '',
                o: ''
            }
        };
    },

    onLayerClicked: function(e) {
        var $el = $(e.target),
            cid = $el.data('cid'),
            layers = this.getCollection(),
            model = layers.get(cid);
        layers.setActiveLayer(model);
    },

    render: function() {
        var self = this,
            makeLayerItem = function(layer) {
                return <LayerItem model={layer}
                                  key={layer.cid}
                                  ref={'layer-' + layer.cid}
                                  onLayerClicked={self.onLayerClicked} />;
            },
            layerItems = this.getCollection().map(makeLayerItem),
            className = 'tab-pane animated fadeInLeft',
            pager = '',
            uploadButton = '';

        if (this.props.active) {
            className += ' active';
        }

        if (layerItems.length === 0) {
            layerItems = (
                <div className="panel panel-default">
                    <div className="panel-body">
                        No items have been added yet.
                    </div>
                </div>
            );
        }

        if (this.getCollection().pages > 1) {
            var prev = '',
                next = '';
            if (this.getCollection().hasPrev()) {
                prev = <li className="previous"><a href="#" onClick={this.prevPage}>Previous</a></li>;
            }
            if (this.getCollection().hasNext()) {
                next = <li className="next"><a href="#" onClick={this.nextPage}>Next</a></li>;
            }
            pager = (
                <nav>
                    <ul className="pager">
                        {prev}
                        {next}
                    </ul>
                </nav>
            );
        }

        if (this.props.uploadsEnabled) {
            uploadButton = (
                <span className="tool-toggle" data-toggle="tooltip" data-placement="bottom" title="Import Imagery Layer">
                    <button className="btn btn-secondary" id="import-imagery" type="button" data-toggle="modal" data-target="#import-imagery-modal">
                        <i className="rf-icon-upload"></i>
                    </button>
                </span>
            );
        }

        return (
            <div role="tabpanel" className={className} id={this.props.id}>
                <div className="sidebar-utility-toolbar">
                    {/* Sorting, Search, Add to workspace */}
                    <div className="utility-tools col-2">
                        {uploadButton}
                        <span className="tool-toggle" data-toggle="tooltip" data-placement="bottom" title="Search Imports">
                            <button className="btn btn-default" type="button"
                                data-toggle="collapse"
                                data-target={ '#search-imagery-' + this.props.id }
                                aria-expanded="false" aria-controls="collapseExample">
                                <i className="rf-icon-search"></i>
                            </button>
                        </span>
                        <span className="tool-toggle" data-toggle="tooltip" data-placement="bottom" title="Sort Imports">
                            <button className="btn btn-default" type="button"
                                data-toggle="collapse" data-target={ '#sort-' + this.props.id }
                                aria-expanded="false" aria-controls="collapseExample">
                                <i className="rf-icon-sort-alt-up"></i>
                            </button>
                        </span>
                    </div>
                    {/* Expandable Search and Filter blocks */}
                </div>
                <div className="collapse" id={ 'search-imagery-' + this.props.id }>
                    <div className="panel panel-default">
                        <div className="panel-body">
                            <form>
                                <fieldset>
                                    <input type="text"
                                        className="form-control"
                                        placeholder="Search by name, organization or tag"
                                        onChange={this.triggerSearch} />
                                </fieldset>
                            </form>
                        </div>
                    </div>
                </div>
                <div className="collapse" id={ 'sort-' + this.props.id }>
                    <div className="panel panel-default">
                        <div className="panel-body">
                            <form>
                                <fieldset>
                                    <select defaultValue="" className="form-control" onChange={this.triggerSort}>
                                        <option value="">--Choose--</option>
                                        <option value="area">Area</option>
                                        <option value="capture_start">Capture Start Date</option>
                                        <option value="capture_end">Capture End Date</option>
                                        <option value="srid">Source Data Projection</option>
                                    </select>
                                </fieldset>
                            </form>
                        </div>
                    </div>
                </div>
                <div className="list-group">
                    {pager}
                    {layerItems}
                    {pager}
                </div>
            </div>
        );
    },

    triggerSearch: function(e) {
        e.persist();
        this.debouncedTriggerSearch(e);
    },

    debouncedTriggerSearch: _.debounce(function(e) {
        var search = (e.target.value).toLowerCase(),
            fetchData = this.state.fetchData;

        fetchData.name_search = search ? search : '';
        this.setState({ fetchData: fetchData });
        this.getCollection().fetch({ data: fetchData });
    }, 200),

    triggerSort: function(e) {
        e.persist();
        this.debouncedTriggerSort(e);
    },

    debouncedTriggerSort: _.debounce(function(e) {
        var prop = e.target.value,
            fetchData = this.state.fetchData;

        fetchData.o = prop ? prop : '';
        this.setState({ fetchData: fetchData });
        this.getCollection().fetch({ data: fetchData });
    }, 200),

    nextPage: function() {
        this.getCollection().getNextPage();
    },

    prevPage: function() {
        this.getCollection().getPrevPage();
    }
});


var LayerItem = React.createBackboneClass({
    render: function() {
        var model = this.getModel(),
            currentUser = settings.getUser(),
            favorite = currentUser.hasFavorited(model),
            isOwner = model.get('username') === currentUser.get('username');

        var actions = '';
        if (isOwner) {
            actions = (
                <div className="list-group-actions">
                    <div className="dropdown">
                        <button type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" className="btn btn-default">
                        <i className="rf-icon-ellipsis"></i>
                        </button>
                        <ul className="dropdown-menu dropdown-menu-dark" aria-labelledby="dLabel">
                            {/*<li><a href="#" onClick={this.editMetaData}>Edit Metadata</a></li>*/}
                            {/*<li><a href="#" onClick={this.importOptions}>Import Options</a></li>*/}
                            {/*<li className="divider"></li>*/}
                            <li><a href="#" onClick={this.deleteLayer} className="text-danger">Delete</a></li>
                        </ul>
                    </div>
                </div>
            );
        }

        return (
            <div className="list-group-item link">
                <a className="list-group-link" href="#"
                    data-cid={this.getModel().cid}
                    onClick={this.props.onLayerClicked}></a>
                <div className="list-group-detail">
                    <img src={this.getModel().get('thumb_small')  || 'https://placehold.it/80x80'} />
                </div>
                <div className="list-group-content">
                    <h5>{this.getModel().get('name')}</h5>
                    <p>{this.getModel().get('organization')}</p>
                </div>
                <div className="list-group-tool">
                    <button type="button" data-toggle="button" aria-pressed="true" autoComplete="off"
                        className={'btn btn-toggle-control btn-favorite ' + (favorite ? 'active' : '')}
                        onClick={this.toggleFavorite}>
                        <i className="rf-icon-star control-active text-primary"></i>
                        <i className="rf-icon-star-empty control-inactive"></i>
                    </button>
                </div>
                {actions}
            </div>
        );
    },

    editMetaData: function() {
        console.log('edit metadata');
    },

    deleteLayer: function() {
        this.getModel().destroy();
        settings.getPendingLayers().remove(this.getModel());
    },

    importOptions: function() {
        console.log('trigger options');
    },

    toggleFavorite: function() {
        var model = this.getModel(),
            currentUser = settings.getUser();
        currentUser.toggleFavorite(model);
    }
});

var ImageMetadataLink = React.createBackboneClass({
    render: function() {
        var image = this.props.image;
        return (
            <div className="list-group-item link">
                <div className="list-group-detail">
                    <img src={image.thumb_small || 'https://placehold.it/80x80'} />
                </div>
                <div className="list-group-content">
                    <h5>{image.file_name}</h5>
                    <a href="#" className="view-metadata" data-id={image.id}
                        onClick={this.props.onClick}>View Metadata</a>
                </div>
            </div>
        );
    }
});

var LayerMetadata = React.createBackboneClass({
    mixins: [
        mixins.LayersMixin(),
        mixins.SlideInMixin
    ],

    onClose: function() {
        this.hideActiveLayer();
    },

    showImageDetail: function(e) {
        var $el = $(e.target),
            id = $el.data('id');
        this.setActiveImage(id);
    },

    zoomToExtent: function() {
        var layer = this.getActiveLayer();
        if (layer) {
            // Updating the map model will trigger a Map component update.
            // The name of the attribute doesn't matter, but the value must
            // be unique. Otherwise, the "attribute changed" event will only
            // fire the first time this action is executed and subsequent
            // clicks will not update the component.
            this.props.mapModel.set('redraw', Math.random());
        }
    },

    render: function() {
        var self = this,
            layer = this.getActiveLayer(),
            tags = 'N/A',
            actions = '';

        if (!layer) {
            return null;
        }

        if (layer.get('tags').length > 0) {
            tags = layer.get('tags').reduce(function(left, right) {
                return <span>{left}, {right}</span>;
            });
        }

        if (layer.getBounds()) {
            actions = (
                <div className="text-right">
                    <a href="#" onClick={this.zoomToExtent}><i className="rf-icon-zoom-in"></i> Zoom to Exent</a>
                </div>
            );
        }

        var images = layer.get('images').map(function(image, i) {
            return <ImageMetadataLink image={image} key={i}
                    onClick={self.showImageDetail} />;
        });

        return (
            <div className={'layer-detail animated active ' + this.state.slide}>
                <div className="sidebar-utility-toolbar">
                    <div className="utility-tools col-2">
                        <ul className="nav nav-tabs" role="tablist">
                            <li role="presentation" className="active"><a href="#layer-detail" aria-controls="layer-detail" role="tab" data-toggle="tab">Metadata</a></li>
                            <li role="presentation"><a href="#layer-images" aria-controls="layer-images" role="tab" data-toggle="tab">Images</a></li>
                        </ul>
                    </div>
                    <div className="utility-tools col-2 text-right">
                        <button type="button" className="close"
                            onClick={this.close}><i className=" rf-icon-cancel"></i></button>
                    </div>
                </div>
                <div className="tab-content">
                    <div role="tabpanel" className="tab-pane active" id="layer-detail">
                        <div className="layer-detail-content">
                            <h4>{layer.get('name')}</h4>
                            <img className="img-preview" src={layer.get('thumb_large') || 'https://placehold.it/400x150'} />
                            {actions}
                            <p>
                                {layer.get('description')}
                            </p>
                            <hr />
                            <dl>
                                <dt>Name:</dt>
                                <dd>{layer.get('name')}</dd>
                                <dt>Organization:</dt>
                                <dd>{layer.get('organization')}</dd>
                                <dt>Capture Start Date:</dt>
                                <dd>{layer.get('capture_start')}</dd>
                                <dt>Capture End Date:</dt>
                                <dd>{layer.get('capture_end')}</dd>
                                <dt>Area:</dt>
                                <dd>{layer.get('area')} {layer.get('area_unit')}</dd>
                                <dt>Source Data Projection: </dt>
                                <dd>{layer.get('projection')}</dd>
                                <dt>Total Images: </dt>
                                <dd>{layer.get('images').length}</dd>
                                <dt>Tags:</dt>
                                <dd>
                                    {tags}
                                </dd>
                            </dl>
                        </div>
                    </div>
                    <div role="tabpanel" className="tab-pane" id="layer-images" key="layer-images">
                        <div className="list-group">
                            {images}
                        </div>
                    </div>
                </div>
            </div>
        );
    }
});

var ImageMetadata = React.createBackboneClass({
    mixins: [
        mixins.LayersMixin(),
        mixins.SlideInMixin
    ],

    onClose: function() {
        this.hideActiveImage();
    },

    render: function() {
        var layer = this.getActiveLayer(),
            image = layer && layer.getActiveImage(),
            metadata = null,
            noMetadataMessage = null;

        if (!image) {
            return null;
        }

        metadata = JSON.parse(image.meta_json);
        metadata = _.sortBy(metadata, 'label');
        if (_.isEmpty(metadata)) {
            noMetadataMessage = (<p>No image metadata found.</p>);
        }

        return (
            <div className={'image-metadata animated active ' + this.state.slide}>
                <div className="sidebar-utility-toolbar">
                    <div className="utility-tools col-2">
                        <h5 className="font-300">Image Metadata: </h5>
                        <h4>{image.file_name}</h4>
                    </div>
                    <div className="utility-tools col-2 text-right">
                        <button type="button" className="close" onClick={this.close}><i className=" rf-icon-cancel"></i></button>
                    </div>
                </div>
                <div className="layer-detail-content">
                    <img className="img-preview" src={image.thumb_large || 'https://placehold.it/300x300'} />
                    <hr />
                    <div className="scrollable">
                        {noMetadataMessage}
                        {_.map(metadata, function(data, i) {
                            return (
                                <dl key={i}>
                                    <dt>{data.label}: </dt>
                                    <dd>{data.value}</dd>
                                </dl>
                            );
                        })}
                    </div>
                </div>
            </div>
        );
    }
});

module.exports = Library;
