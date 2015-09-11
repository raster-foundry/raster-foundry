'use strict';

var $ = require('jquery'),
    _ = require('underscore'),
    React = require('react'),
    Map = require('./map'),
    DropdownMenu = require('./menu').DropdownMenu,
    settings = require('../../settings');


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

        // Layer metadata
        $('.layer-detail .close').click(function(evt) {
            evt.preventDefault();
            var layerDetail = $('.layer-detail');
            layerDetail.addClass('slideOutLeft');
            setTimeout(function() {
                layerDetail.removeClass('slideOutLeft active');
            }, 400);
        });

        // Image metadata
        var imageMetadata = $('.image-metadata');
        $('.view-metadata').click(function(evt) {
            evt.preventDefault();
            imageMetadata.addClass('active');
        });

        $('.image-metadata .close').click(function(evt) {
            evt.preventDefault();
            imageMetadata.addClass('slideOutLeft');
            setTimeout(function() {
                imageMetadata.removeClass('slideOutLeft active');
            }, 400);
        });

        // Layer tools
        $('.select-all').click(function() {
            $(this).parent('.utility-tools-secondary').toggleClass('active');
        });
    },

    render: function() {
        return (
            <div>
                <div className="sidebar">
                    <Sidebar layers={this.props.layers}/>
                </div>
                <Map />
            </div>
        );
    }
});

var Sidebar = React.createBackboneClass({
    render: function() {
        return (
            <div>
                <DropdownMenu />
                <div className="sidebar-utility" role="tabpanel">
                    <Tabs />
                    <div className="sidebar-utility-content">
                        <TabContents layers={this.props.layers}/>
                    </div>
                    <LayerMetadata />
                    <ImageMetadata />
                </div>
            </div>
        );
    }
});

var Tabs = React.createBackboneClass({
    render: function() {
        return (
            <div className="sidebar-utility-header">
                <ul className="nav nav-tabs nav-tabs-dark" role="tablist">
                    <li role="presentation" className="active"><a href="#imports" aria-controls="imports" role="tab" data-toggle="tab">My Imports</a></li>
                    <li role="presentation"><a href="#catalog" aria-controls="catalog" role="tab" data-toggle="tab">Public Catalog</a></li>
                    <li role="presentation"><a href="#favorites" aria-controls="favorites" role="tab" data-toggle="tab">Favorites</a></li>
                    <li role="presentation"><a href="#processing" aria-controls="processing" role="tab" data-toggle="tab">Processing</a></li>
                </ul>
            </div>
        );
    }
});

var TabContents = React.createBackboneClass({
    render: function() {
        return (
            <div className="tab-content">
                {/* Imports Tab Pane */}
                <LayerCollection collection={this.props.layers.myLayerItems} id="imports" active={true} uploadsEnabled={true} />
                {/* /#imports.tab-pane */}

                {/* Catalog Tab Pane */}
                <LayerCollection collection={this.props.layers.publicLayerItems} id="catalog" />
                {/* /#catalog.tab-pane */}

                {/* Favorites Tab Pane */}
                <LayerCollection collection={this.props.layers.favoriteLayerItems} id="favorites" />
                {/* /#favorites.tab-pane */}

                {/* Processing Tab Pane */}
                <div role="tabpanel" className="tab-pane animated fadeInLeft" id="processing">
                    {/* Imagery Layers */}
                    <div className="list-group">
                        {/* Processing List Group Item */}
                        <div className="list-group-item">
                            <div className="list-group-detail">
                                <img src="http://placehold.it/200x200" />
                            </div>
                            <div className="list-group-content">
                                <h5>Imagery Layer Name</h5>
                                <div className="progress">
                                    <div className="progress-bar" role="progressbar" aria-valuenow="60" aria-valuemin="0" aria-valuemax="100" style={{width: '60%'}}>
                                        <span className="sr-only">60% Complete</span>
                                    </div>
                                </div>
                            </div>
                            <div className="list-group-tool">
                                <button type="button" className="btn btn-toggle-control">
                                <i className="rf-icon-cancel text-danger"></i>
                                </button>
                            </div>
                        </div>
                        {/* Processing List Group Item */}
                        <div className="list-group-item">
                            <div className="list-group-detail">
                                <img src="http://placehold.it/200x200" />
                            </div>
                            <div className="list-group-content">
                                <h5>Imagery Layer Name</h5>
                                <div className="progress">
                                    <div className="progress-bar" role="progressbar" aria-valuenow="60" aria-valuemin="0" aria-valuemax="100" style={{width: '60%'}}>
                                        <span className="sr-only">60% Complete</span>
                                    </div>
                                </div>
                            </div>
                            <div className="list-group-tool">
                                <button type="button" className="btn btn-toggle-control">
                                <i className="rf-icon-cancel text-danger"></i>
                                </button>
                            </div>
                        </div>
                    </div>
                    {/* /#processing.tab-pane */}
                </div>
            </div>
        );
    }
});

var LayerCollection = React.createBackboneClass({
    getInitialState: function() {
        return {
            selectAll: false,
            visibleLayers: this.getCollection(),
            filterFn: _.identity,
            sortFn: _.identity
        };
    },

    render: function() {
        var selected = this.state.selectAll,
            makeLayerItem = function(layer) {
                return <LayerItem model={layer} key={layer.cid} selected={selected} ref={'layer-' + layer.cid} />;
            },
            layerItems = this.getCollection()
                             .toArray()
                             .filter(this.state.filterFn)
                             .sort(this.state.sortFn)
                             .map(makeLayerItem),
            className = 'tab-pane animated fadeInLeft';

        if (this.props.active) {
            className += ' active';
        }

        var uploadButton = '';
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
                    <div className="utility-tools col-2 text-right utility-tools-secondary">
                        <button className="btn btn-danger btn-sm"><i className="rf-icon-trash-empty"></i> Delete</button>
                        <button className="btn btn-primary btn-sm" data-toggle="modal" data-target="#addto-imagery-modal"><i className="rf-icon-plus"></i> Add to</button>
                        <div className="checkbox toggle-all select-all">
                            <input type="checkbox" checked={this.state.selectAll} onChange={this.toggleSelectAll} />
                            <label></label>
                        </div>
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
                    { layerItems }
                </div>
            </div>
        );
    },

    toggleSelectAll: function() {
        var state = !this.state.selectAll;
        this.setState({ selectAll: state });
        this.getCollection().map(function(layer) {
            this.refs['layer-' + layer.cid].toggleState(state);
        }, this);
    },

    triggerSearch: function(e) {
        var search = (e.target.value).toLowerCase(),
            filterFn = _.identity;
        if (search && search !== '') {
            filterFn = function(item) {
                var nameMatch = item.get('name').toLowerCase().indexOf(search) > -1,
                    organizationMatch = item.get('organization').toLowerCase().indexOf(search) > -1;
                return nameMatch || organizationMatch;
            };
        }
        this.setState({ filterFn: filterFn });
    },

    triggerSort: function(e) {
        var prop = e.target.value,
            sortFn = _.identity,
            dateSorter = function(a, b) {
                // Default to most recent first.
                // Therefore if b is after a, we want b to come first.
                // Real dates should come before null.
                if (a && b) {
                    if (a.isSame(b)) {
                        return 0;
                    }
                    return b.isAfter(a) ? 1 : -1;
                } else if (a) {
                    return -1;
                } else {
                    return 1;
                }
            },
            numberSorter = function(a, b) {
                return a - b;
            },
            stringSorter = function(a, b) {
                if (a === b) {
                    return 0;
                }
                return a > b ? 1 : -1;
            },
            mapping = {
                'area': numberSorter,
                'capture_start': dateSorter,
                'capture_end': dateSorter,
                'srid': stringSorter
            };

        if (prop && prop !== '') {
            var sorter = mapping[prop];
            sortFn = function(a, b) {
                return sorter(a.get(prop), b.get(prop));
            };
        }
        this.setState({ sortFn: sortFn });
    }
});


var LayerItem = React.createBackboneClass({
    componentDidMount: function() {
        var currentUser = settings.getUser(),
            self = this;

        currentUser.on('change', function() {
            self.setState({
                currentUserId: this.get('id')
            });
        });
    },

    getInitialState: function() {
        var currentUser = settings.getUser();
        return { currentUserId: currentUser.get('id') };
    },

    render: function() {
        var actions = '';
        if (Number(this.getModel().get('owner')) === this.state.currentUserId) {
            actions = (
                <div className="list-group-actions">
                    <div className="dropdown">
                        <button type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" className="btn btn-default">
                        <i className="rf-icon-ellipsis"></i>
                        </button>
                        <ul className="dropdown-menu dropdown-menu-dark" aria-labelledby="dLabel">
                            <li><a href="#" onClick={this.editMetaData}>Edit Metadata</a></li>
                            <li><a href="#" onClick={this.importOptions}>Import Options</a></li>
                            <li className="divider"></li>
                            <li><a href="#" onClick={this.deleteLayer} className="text-danger">Delete</a></li>
                        </ul>
                    </div>
                </div>
            );
        }

        return (
            <div className="list-group-item link">
                <a className="list-group-link" href="#" onClick={this.triggerLayerDetail}></a>
                <div className="list-group-detail">
                    <img src="http://placehold.it/200x200" />
                </div>
                <div className="list-group-content">
                    <h5>{this.getModel().get('name')}</h5>
                    <p>{this.getModel().get('organization')}</p>
                </div>
                <div className="list-group-tool">
                    <button type="button" className="btn btn-toggle-control btn-favorite" data-toggle="button" aria-pressed="true" autoComplete="off" onClick={this.markAsFavorite}>
                        <i className="rf-icon-star control-active text-primary"></i>
                        <i className="rf-icon-star-empty control-inactive"></i>
                    </button>
                    <div className="checkbox">
                        <input type="checkbox" checked={this.state.selected} onChange={this.selectItem} />
                        <label></label>
                    </div>
                </div>
                {actions}
            </div>
        );
    },

    editMetaData: function() {
        console.log('edit metadata');
    },

    deleteLayer: function() {
        console.log('delete layer');
    },

    importOptions: function() {
        console.log('trigger options');
    },

    markAsFavorite: function(e) {
        console.log('favorite this layer');
        // TODO This won't fire in Firefox. Seems like the element that triggers
        // the layer detail panel is capturing the event.
        e.stopPropagation();
        e.preventDefault();
    },

    triggerLayerDetail: function(e) {
        console.log('detail');
        var layerDetail = $('.layer-detail');
        e.preventDefault();
        layerDetail.addClass('active');
    },

    selectItem: function() {
        this.setState({ selected: !this.state.selected });
    },

    toggleState: function(selected) {
        this.setState({ selected: selected });
    }
});


var LayerMetadata = React.createBackboneClass({
    render: function() {
        return (
            <div className="layer-detail animated slideInLeft">
                <div className="sidebar-utility-toolbar">
                    <div className="utility-tools col-2">
                        <ul className="nav nav-tabs" role="tablist">
                            <li role="presentation" className="active"><a href="#layer-detail" aria-controls="layer-detail" role="tab" data-toggle="tab">Metadata</a></li>
                            <li role="presentation"><a href="#layer-images" aria-controls="layer-images" role="tab" data-toggle="tab">Images</a></li>
                        </ul>
                    </div>
                    <div className="utility-tools col-2 text-right">
                        <button type="button" className="close"><i className=" rf-icon-cancel"></i></button>
                    </div>
                </div>
                <div className="tab-content">
                    <div  role="tabpanel" className="tab-pane active" id="layer-detail">
                        <div className="layer-detail-content">
                            <h4>Imagery Layer Name 1</h4>
                            <img className="img-preview" src="http://placehold.it/400x150" />
                            <p>These Landsat 7 composites are made from Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex. </p>
                            <p>Ea commodo consequat. Duis aute irure dolor in reprehend in voluptate velit esse cill.</p>
                            <hr />
                            <dl>
                                <dt>Title:</dt>
                                <dd>Imagery Layer Name 1</dd>
                                <dt>Provider:</dt>
                                <dd>Organization Name</dd>
                                <dt>Capture Start Date:</dt>
                                <dd>March 15, 2015</dd>
                                <dt>Capture End Date:</dt>
                                <dd>March 30, 2015</dd>
                                <dt>Area:</dt>
                                <dd>120 sq. Miles</dd>
                                <dt>Source Data Projection: </dt>
                                <dd>N/A</dd>
                                <dt>Total Images: </dt>
                                <dd>162</dd>
                                <dt>Tags:</dt>
                                <dd>
                                    <a href="#">earthquake</a>,
                                    <a href="#">damage assessment </a>,
                                    <a href="#">evi</a>,
                                    <a href="#">Landsat</a>
                                </dd>
                            </dl>
                        </div>
                    </div>
                    <div  role="tabpanel" className="tab-pane" id="layer-images">
                        <div className="list-group">
                            <div className="list-group-item link">
                                <div className="list-group-detail">
                                    <img src="http://placehold.it/200x200" />
                                </div>
                                <div className="list-group-content">
                                    <h5>P1020861.JPG</h5>
                                    <a href="#" className="view-metadata">View Metadata</a>
                                </div>
                            </div>
                            <div className="list-group-item link">
                                <div className="list-group-detail">
                                    <img src="http://placehold.it/200x200" />
                                </div>
                                <div className="list-group-content">
                                    <h5>P1020861.JPG</h5>
                                    <a href="#" className="view-metadata">View Metadata</a>
                                </div>
                            </div>
                            <div className="list-group-item link">
                                <div className="list-group-detail">
                                    <img src="http://placehold.it/200x200" />
                                </div>
                                <div className="list-group-content">
                                    <h5>P1020861.JPG</h5>
                                    <a href="#" className="view-metadata">View Metadata</a>
                                </div>
                            </div>
                            <div className="list-group-item link">
                                <div className="list-group-detail">
                                    <img src="http://placehold.it/200x200" />
                                </div>
                                <div className="list-group-content">
                                    <h5>P1020861.JPG</h5>
                                    <a href="#" className="view-metadata">View Metadata</a>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }
});

var ImageMetadata = React.createBackboneClass({
    render: function() {
        return (
            <div className="image-metadata animated slideInLeft">
                <div className="sidebar-utility-toolbar">
                    <div className="utility-tools col-2">
                        <h5 className="font-300">Image Metadata: </h5>
                        <h4>P1020861.JPG</h4>
                    </div>
                    <div className="utility-tools col-2 text-right">
                        <button type="button" className="close"><i className=" rf-icon-cancel"></i></button>
                    </div>
                </div>
                <div className="layer-detail-content">
                    <img className="img-preview" src="http://placehold.it/300x300" />
                    <hr />
                    <dl>
                        <dt>Acquistion: </dt>
                        <dd>11-12-2015</dd>
                        <dt>Local Time of Day: </dt>
                        <dd>15:42</dd>
                        <dt>Latitude: </dt>
                        <dd>39.226º</dd>
                        <dt>Longitude: </dt>
                        <dd>-74.892º</dd>
                        <dt>Image Quality: </dt>
                        <dd>Standard</dd>
                        <dt>SNR: </dt>
                        <dd>75.30</dd>
                        <dt>Sun Alittude: </dt>
                        <dd>N/A</dd>
                        <dt>Sun Longitude: </dt>
                        <dd>N/A</dd>
                        <dt>Satellite ID: </dt>
                        <dd>N/A</dd>
                    </dl>
                    <dl>
                        <dt>Camera Bit Depth: </dt>
                        <dd>10</dd>
                        <dt>Camera Color Mode:</dt>
                        <dd>RGB</dd>
                        <dt>Camera Exposure Time: </dt>
                        <dd>1170μs</dd>
                        <dt>Camera Gain: </dt>
                        <dd>350</dd>
                        <dt>Camera TDI Pulses: </dt>
                        <dd>15</dd>
                        <dt>Cloud Cover: </dt>
                        <dd>0.26%</dd>
                        <dt>File Size: </dt>
                        <dd>58MB</dd>
                        <dt>GSD: </dt>
                        <dd>4.25 m</dd>
                    </dl>
                </div>
            </div>
        );
    }
});

module.exports = Library;
