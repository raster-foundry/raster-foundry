'use strict';

var $ = require('jquery'),
    _ = require('underscore'),
    React = require('react'),
    asset = require('../../core/utils').asset;

var DropdownMenu = React.createBackboneClass({
    menuItems: [
        { id: 'library', name: 'Library', url: '/', icon: 'rf-icon-book' },
        '---',
        { id: 'account', name: 'Account', url: '/account', icon: 'rf-icon-user' },
        { id: 'logout', name: 'Logout', url: '/logout', icon: 'rf-icon-logout' }
    ],

    componentDidMount: function() {
        $('#dl-menu').dlmenu();
    },

    componentWillUnmount: function() {
        $('#dl-menu').off('.dlmenu');
    },

    render: function() {
        var selected = this.props.selected || 'library',
            selectedItem = _.find(this.menuItems, { id: selected });
        return (
            <div className="sidebar-header">
                <a className="sidebar-brand" href="/">
                    <img src={asset('img/logo-raster-foundry.png')} />
                </a>
                <div id="dl-menu" className="dl-menuwrapper">
                    <button className="dl-trigger">
                        <span className="pull-right">
                            <span className="icon-bar"></span>
                            <span className="icon-bar"></span>
                            <span className="icon-bar"></span>
                        </span>
                        <i className={selectedItem.icon}></i>
                        {selectedItem.name}
                    </button>
                    <ul className="dl-menu">
                        {/*
                        <li className="dl-menu-group-title"><i className="rf-icon-map-alt"></i> Mosaic Projects</li>
                        <li className="dl-menu-group">
                            <a href="/project.html" className="new-project">New Mosaic Project</a>
                            <a href="/project-with-layers.html">Project Name 6</a>
                            <a href="/project-with-layers.html">Project Name 2</a>
                            <a href="/project-with-layers.html">Project Name 1</a>
                        </li>
                        <li className="dl-menu-group">
                            <a href="#" className="sub-menu-parent">More Mosaic Projects</a>
                            <ul className="dl-submenu">
                                <li><a href="/project.html" className="new-project">New Mosaic Project</a></li>
                                <li><a href="/project-with-layers.html">Project Name 1</a></li>
                                <li><a href="/project-with-layers.html">Project Name 2</a></li>
                                <li><a href="/project-with-layers.html">Project Name 3</a></li>
                                <li><a href="/project-with-layers.html">Project Name 4</a></li>
                                <li><a href="/project-with-layers.html">Project Name 5</a></li>
                                <li><a href="/project-with-layers.html">Project Name 6</a></li>
                            </ul>
                        </li>
                        */}
                        {this.menuItems.map(function(item) {
                            if (item === '---') {
                                return <li key="sep" className="divider"></li>;
                            }
                            return (
                                <li key={'menu-li-' + item.id}>
                                    <a href={item.url}
                                       data-url={item.url}
                                       className={item.id === selected ? 'active' : ''}
                                       ><i className={item.icon}></i> {item.name}</a>
                                </li>
                            );
                        })}
                    </ul>
                </div>
            </div>
        );
    }
});

var HeaderMenu = React.createBackboneClass({
    render: function() {
        var selected = this.props.selected;
        return (
            <div className="nav">
                {this.props.items.map(function(item) {
                    return <a href={item.url}
                              data-url={item.url}
                              key={item.url}
                              className={item.id=== selected ? 'active' : ''}
                              >{item.name}</a>;
                })}
            </div>
       );
    }
});

module.exports = {
    DropdownMenu: DropdownMenu,
    HeaderMenu: HeaderMenu
};
