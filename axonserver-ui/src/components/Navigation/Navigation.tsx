import React from 'react';
import { Link } from '../Link/Link';
import { Typography } from '../Typography/Typography';
import classnames from "classnames";

import SettingsIcon from '@material-ui/icons/Settings';
import VisibilityIcon from '@material-ui/icons/Visibility';
import SearchIcon from '@material-ui/icons/Search';
import ErrorIcon from '@material-ui/icons/Error';
import HelpIcon from '@material-ui/icons/Help';
import GroupIcon from '@material-ui/icons/Group';

import './navigation.scss';

export type NavigationItem = 'settings' | 'overview' | 'search' | 'commands' | 'queries' | 'users';
type NavigationProps = {
    active?: NavigationItem
}

export const Navigation = (props: NavigationProps) => (
    <div className="navigation">
        <Link
            to=""
            active={props.active === 'settings'}>
            <div className={classnames("navigation__link", {"navigation__link--active": props.active === 'settings'})}>
                <div className="navigation__link-icon">
                    <SettingsIcon fontSize="inherit"/>
                </div>
                <Typography weight="bold" size="s">Settings</Typography>
            </div>
        </Link>
        <Link to="" active={props.active === 'overview'}>
        <div className={classnames("navigation__link", {"navigation__link--active": props.active === 'overview'})}>
                <div className="navigation__link-icon">
                    <VisibilityIcon fontSize="inherit"/>
                </div>
                <Typography weight="bold" size="s">Overview</Typography>
            </div>
        </Link>
        <Link to="" active={props.active === 'search'}>
        <div className={classnames("navigation__link", {"navigation__link--active": props.active === 'search'})}>
                <div className="navigation__link-icon">
                    <SearchIcon fontSize="inherit"/>
                </div>
                <Typography weight="bold" size="s">Search</Typography>
            </div>
        </Link>
        <Link to="" active={props.active === 'commands'}>
        <div className={classnames("navigation__link", {"navigation__link--active": props.active === 'commands'})}>
                <div className="navigation__link-icon">
                    <ErrorIcon fontSize="inherit"/>
                </div>
                <Typography weight="bold" size="s">Commands</Typography>
            </div>
        </Link>
        <Link to="" active={props.active === 'queries'}>
        <div className={classnames("navigation__link", {"navigation__link--active": props.active === 'queries'})}>
                <div className="navigation__link-icon">
                    <HelpIcon fontSize="inherit"/>
                </div>
                <Typography weight="bold" size="s">Queries</Typography>
            </div>
        </Link>
        <Link to="" active={props.active === 'users'}>
        <div className={classnames("navigation__link", {"navigation__link--active": props.active === 'users'})}>
                <div className="navigation__link-icon">
                    <GroupIcon fontSize="inherit"/>
                </div>
                <Typography weight="bold" size="s">Users</Typography>
            </div>
        </Link>
    </div>
);