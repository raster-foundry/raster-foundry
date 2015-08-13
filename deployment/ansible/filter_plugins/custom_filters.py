class FilterModule(object):
    '''
    Custom filters to guard specific Ansible task by environment using set-like
    operations.
    '''

    def filters(self):
        return {
            'is_not_in': self.is_not_in,
            'is_in': self.is_in,
            'some_are_in': self.some_are_in
        }

    def is_not_in(self, x, y):
        """Determines if there are no elements in common between x and y

            x | is_not_in(y)

        Arguments
        :param t: A tuple with two elements (x and y)
        """
        return set(x).isdisjoint(set(y))

    def is_in(self, x, y):
        """Determines if all of the elements in x are a subset of y

            x | is_in(y)

        Arguments
        :param t: A tuple with two elements (x and y)
        """
        return set(x).issubset(set(y))

    def some_are_in(self, x, y):
        """Determines if any element in x intersects with y

            x | some_are_in(y)

        Arguments
        :param t: A tuple with two elements (x and y)
        """
        return len(set(x) & set(y)) > 0
