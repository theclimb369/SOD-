<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns:xsd="http://www.w3.org/2001/XMLSchema" >

  <xsl:output method="html"/>

  <xsl:template match="/">
    <html>
      <head>
        <title>Sod Tag Documentation</title>
      </head>
      <body>
        <h1>Sod Tag Documentation</h1>
         <xsl:apply-templates select="xsd:schema" />
      </body>
    </html>
  </xsl:template>

  <xsl:template match="xsd:schema" >
    <p>Doing a schema</p>
    <xsl:variable name="filelist" >
      <xsl:apply-templates select="xsd:include" mode="findfiles" >
      </xsl:apply-templates>
    </xsl:variable>
    <xsl:copy-of select="$filelist"/>

    <xsl:for-each select="xsd:include" >
      <xsl:value-of select="@schemaLocation"/><br/>
    </xsl:for-each>
    <xsl:for-each select="xsd:include" >
      <p>Found an include aaa <xsl:value-of select="@schemaLocation" /></p>
      <xsl:apply-templates select="document(@schemaLocation)/xsd:schema/xsd:complexType|document(@schemaLocation)/xsd:schema/xsd:element" />
    </xsl:for-each>
    <xsl:apply-templates select="xsd:complexType|xsd:element" />
  </xsl:template>



  <xsl:template match="xsd:complexType" >
    <xsl:variable name="tag-name" select="@name"/>
    <h3 id="{$tag-name}" >
      <xsl:if test="@abstract='true'" >
        <xsl:text>Abstract </xsl:text>    
      </xsl:if>
      <xsl:value-of select="@name" />
    </h3>
    <xsl:if test="xsd:complexContent/xsd:extension">
      <p>
        <xsl:text> extends </xsl:text>
        <xsl:variable name="base" select="xsd:complexContent/xsd:extension/@base"/>
        <a href="#{$base}">
          <xsl:value-of select="$base"/>
        </a>
      </p>
    </xsl:if>
    <p>
      <h4>
        <xsl:text>All known subclasses</xsl:text>
      </h4>
      <xsl:for-each select="//xsd:complexType[xsd:complexContent/xsd:extension/@base=$tag-name]" >
        <xsl:sort select="@name" />
        <xsl:variable name="subclass" select="@name"/>
        <a href="#{$subclass}">
          <xsl:value-of select="$subclass"/>
        </a>
        <xsl:text> </xsl:text>
      </xsl:for-each>
    </p>
    <p>
      <xsl:apply-templates select="xsd:annotation" />
    </p>
    <p>
      <xsl:apply-templates select="(preceding-sibling::*|preceding-sibling::comment())[last()]" mode="comments"/>
      <h5><xsl:text>Elements</xsl:text></h5>
      <xsl:apply-templates select="xsd:sequence|xsd:complexContent" />
    </p>
    <hr/>
  </xsl:template>

  <xsl:template match="comment()" mode="comments">
    <h5><xsl:text>Comment</xsl:text></h5>
    
    <p>
      <xsl:value-of select="."/>
    </p>
  </xsl:template>  

  <xsl:template match="xsd:complexContent" >
      <xsl:apply-templates select="xsd:extension" />
  </xsl:template>

  <xsl:template match="xsd:extension" >
    <xsl:variable  name="abstractBase">
      <xsl:value-of  select="@base" />
    </xsl:variable>
    <xsl:apply-templates select="//xsd:complexType[@name=$abstractBase]//xsd:sequence" />
    <xsl:apply-templates select="xsd:sequence" />
  </xsl:template>

  <xsl:template match="xsd:sequence" >
      <xsl:apply-templates select="xsd:element" />
  </xsl:template>

  <xsl:template match="xsd:element" >
    <xsl:choose>
      <xsl:when test="@ref">
        <xsl:variable  name="ref">
          <xsl:value-of  select="@ref" />
        </xsl:variable>
        <xsl:apply-templates select="//xsd:element[@name=$ref]" />
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="@name" />
        <xsl:text> </xsl:text>
          <xsl:variable name="type" select="@type" />
        <a href="#{$type}">
          <xsl:value-of select="$type" />
        </a>
        <xsl:choose>
        <xsl:when test=" not( @minOccurs) and not( @maxOccurs)">
          <xsl:text> exactly once</xsl:text>
        </xsl:when>
        <xsl:when test="@minOccurs=0">
          <xsl:text> optional</xsl:text>
        </xsl:when>
        <xsl:when test="@minOccurs | @maxOccurs">
          <xsl:if test="@minOccurs" >
            <xsl:text> occures at least </xsl:text>
            <xsl:value-of select="@minOccures"/>
            <xsl:text> times </xsl:text>
          </xsl:if>
          <xsl:if test="@minOccurs" >
            <xsl:text> and at most </xsl:text>
            <xsl:value-of select="@minOccures"/>
            <xsl:text> times</xsl:text>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text> optional</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
        <br/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="xsd:annotation">
    <xsl:apply-templates select="xsd:documentation" />
  </xsl:template>

  <xsl:template match="xsd:documentation" >
    <h4>Summary</h4>
    <xsl:copy-of select="summary/node()" />
    <h4>Description</h4>
    <xsl:copy-of select="description/node()" />
    <h4>Example</h4>
    <code>
      <xsl:apply-templates select="example/node()" mode="make-literal" />
    </code>
  </xsl:template>

  <xsl:template match="*" mode="make-literal" >
    <xsl:text>&lt;</xsl:text>
    <xsl:value-of select="name()"/>
    <xsl:if test="count(@*)">
      <xsl:text> </xsl:text>
    </xsl:if>
    <xsl:for-each select="@*">
      <xsl:value-of select="name()"/>
      <xsl:text>=&quot;</xsl:text>
      <xsl:value-of select="."/>
      <xsl:text>&quot;</xsl:text>
    </xsl:for-each>
    <xsl:choose>
    <xsl:when test="node()">
      <xsl:text>&gt;</xsl:text>
      <br/>
      <xsl:apply-templates select="node()" mode="make-literal" />
      <xsl:text>&lt;/</xsl:text>
      <xsl:value-of select="name()"/>
      <xsl:text>&gt;</xsl:text>
      <br/>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>/&gt;</xsl:text>
      <br/>
    </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="text()" mode="make-literal" >
    <xsl:value-of select="."/>
    <br/>
  </xsl:template>

</xsl:stylesheet>

