/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import React from 'react';

import Favorite from '../../../components/controls/Favorite';
import QualifierIcon from '../../../components/shared/qualifier-icon';
import { translate } from '../../../helpers/l10n';
import { getComponentUrl } from '../../../helpers/urls';

const Favorites = ({ favorites }) => (
    <section>
      <h2 className="spacer-bottom">
        {translate('my_account.favorite_components')}
      </h2>

      {!favorites.length && (
          <p className="note">
            {translate('my_account.no_favorite_components')}
          </p>
      )}

      <table id="favorite-components" className="data">
        <tbody>
          {favorites.map(f => (
              <tr key={f.key}>
                <td className="thin">
                  <Favorite component={f.key} favorite={true}/>
                </td>
                <td>
                  <a href={getComponentUrl(f.key)} className="link-with-icon">
                    <QualifierIcon qualifier={f.qualifier}/>
                    {' '}
                    <span>{f.name}</span>
                  </a>
                </td>
              </tr>
          ))}
        </tbody>
      </table>

    </section>
);

export default Favorites;
