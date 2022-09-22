/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonar.python.checks.cdk;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;

@Rule(key = "S6303")
public class DisabledRDSEncryptionCheck extends AbstractCdkResourceCheck {
  private static final String UNENCRYPTED_MESSAGE = "Make sure that using unencrypted databases is safe here.";
  private static final String ARG_ENCRYPTED = "storage_encrypted";
  private static final String ARG_ENCRYPTION_KEY = "storage_encryption_key";
  private static final String DB_OMITTING_MESSAGE = "Omitting \""+ARG_ENCRYPTED+"\" and \""+ARG_ENCRYPTION_KEY+"\" disables RDS encryption. Make sure it is safe here.";
  private static final String CFNDB_OMITTING_MESSAGE = "Omitting \""+ARG_ENCRYPTED+"\" disables RDS encryption. Make sure it is safe here.";

  @Override
  protected void registerFqnConsumer() {
    checkFqn("aws_cdk.aws_rds.DatabaseCluster", this::checkDatabaseArguments);
    checkFqn("aws_cdk.aws_rds.DatabaseInstance", this::checkDatabaseArguments);
    checkFqn("aws_cdk.aws_rds.CfnDBCluster", this::checkCfnDatabaseArguments);
    checkFqn("aws_cdk.aws_rds.CfnDBInstance", (subscriptionContext, callExpression) -> {
      if (!isEngineAurora(subscriptionContext, callExpression)) {
        checkCfnDatabaseArguments(subscriptionContext, callExpression);
      }
    });
  }

  protected void checkDatabaseArguments(SubscriptionContext ctx, CallExpression resourceConstructor) {
    Optional<ArgumentTrace> argEncrypted = getArgument(ctx, resourceConstructor, ARG_ENCRYPTED);
    Optional<ArgumentTrace> argEncryptionKey = getArgument(ctx, resourceConstructor, ARG_ENCRYPTION_KEY);

    if (argEncrypted.isEmpty() && argEncryptionKey.isEmpty()) {
      ctx.addIssue(resourceConstructor.callee(), DB_OMITTING_MESSAGE);
    } else if (argEncrypted.isEmpty()) {
      argEncryptionKey.get().addIssueIf(AbstractCdkResourceCheck::isNone, UNENCRYPTED_MESSAGE);
    } else if (argEncryptionKey.isEmpty()) {
      argEncrypted.get().addIssueIf(AbstractCdkResourceCheck::isFalse, UNENCRYPTED_MESSAGE);
    } else {
      if (argEncryptionKey.get().hasExpression(AbstractCdkResourceCheck::isNone)
        && argEncrypted.get().hasExpression(AbstractCdkResourceCheck::isFalse)) {
        argEncrypted.get().addIssue(UNENCRYPTED_MESSAGE);
      }
    }
  }

  protected void checkCfnDatabaseArguments(SubscriptionContext ctx, CallExpression resourceConstructor) {
    getArgument(ctx, resourceConstructor, ARG_ENCRYPTED).ifPresentOrElse(
      argumentTrace -> argumentTrace.addIssueIf(AbstractCdkResourceCheck::isFalse, UNENCRYPTED_MESSAGE),
      () -> ctx.addIssue(resourceConstructor.callee(), CFNDB_OMITTING_MESSAGE)
    );
  }

  protected boolean isEngineAurora(SubscriptionContext ctx, CallExpression resourceConstructor) {
    return getArgument(ctx, resourceConstructor, "engine")
      .filter(argumentTrace -> argumentTrace.hasExpression(startsWith("aurora"))).isPresent();
  }

  protected Predicate<Expression> startsWith(String expected) {
    return e -> getStringValue(e).filter(str -> str.startsWith(expected)).isPresent();
  }

  protected Optional<String> getStringValue(Expression expression) {
    return Optional.of(expression)
      .filter(expr -> expr.is(Tree.Kind.STRING_LITERAL)).map(StringLiteral.class::cast)
      .map(str -> str.trimmedQuotesValue().toLowerCase(Locale.ROOT));
  }
}
