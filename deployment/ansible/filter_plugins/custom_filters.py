class FilterModule(object):
    '''
    Custom filters for set operations on lists:

        - is_not_in
        - is_in
        - some_are_into

    And dictionary merging:

        - combine
    '''

    def filters(self):
        return {
            'is_not_in': self.is_not_in,
            'is_in': self.is_in,
            'some_are_in': self.some_are_in,
            'combine': self.combine,
        }

    def is_not_in(self, x, y):
        """Determines if there are no elements in common between x and y

            x | is_not_in(y)

        Arguments
        :param x: A list
        :param y: A list
        """
        return set(x).isdisjoint(set(y))

    def is_in(self, x, y):
        """Determines if all of the elements in x are a subset of y

            x | is_in(y)

        Arguments
        :param x: A list
        :param y: A list
        """
        return set(x).issubset(set(y))

    def some_are_in(self, x, y):
        """Determines if any element in x intersects with y

            x | some_are_in(y)

        Arguments
        :param x: A list
        :param y: A list
        """
        return len(set(x) & set(y)) > 0

    def combine(self, x, y):
        """Combines the dictionary x with the key/value pairs from y

            x | combine(y)

        Arguments
        :param x: A dictionary
        :param y: A dictionary
        """
        x.update(y)

        return x
