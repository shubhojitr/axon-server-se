import React from 'react';
import { Search } from './Search';

export default {
  title: 'Components/Search',
  component: Search,
};

export const Default = () => (
  <Search onSubmit={(value) => console.log('hi from value -> ', value)} />
);
