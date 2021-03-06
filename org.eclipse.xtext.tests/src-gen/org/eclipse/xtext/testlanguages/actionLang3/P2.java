/**
 * generated by Xtext
 */
package org.eclipse.xtext.testlanguages.actionLang3;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>P2</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.eclipse.xtext.testlanguages.actionLang3.P2#getP <em>P</em>}</li>
 *   <li>{@link org.eclipse.xtext.testlanguages.actionLang3.P2#getString <em>String</em>}</li>
 * </ul>
 *
 * @see org.eclipse.xtext.testlanguages.actionLang3.ActionLang3Package#getP2()
 * @model
 * @generated
 */
public interface P2 extends ProductionRule1
{
  /**
   * Returns the value of the '<em><b>P</b></em>' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return the value of the '<em>P</em>' containment reference.
   * @see #setP(P1)
   * @see org.eclipse.xtext.testlanguages.actionLang3.ActionLang3Package#getP2_P()
   * @model containment="true"
   * @generated
   */
  P1 getP();

  /**
   * Sets the value of the '{@link org.eclipse.xtext.testlanguages.actionLang3.P2#getP <em>P</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>P</em>' containment reference.
   * @see #getP()
   * @generated
   */
  void setP(P1 value);

  /**
   * Returns the value of the '<em><b>String</b></em>' attribute.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return the value of the '<em>String</em>' attribute.
   * @see #setString(String)
   * @see org.eclipse.xtext.testlanguages.actionLang3.ActionLang3Package#getP2_String()
   * @model
   * @generated
   */
  String getString();

  /**
   * Sets the value of the '{@link org.eclipse.xtext.testlanguages.actionLang3.P2#getString <em>String</em>}' attribute.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>String</em>' attribute.
   * @see #getString()
   * @generated
   */
  void setString(String value);

} // P2
